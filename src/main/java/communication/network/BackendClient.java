package communication.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Small HTTP transport for the shared Vercel packet broker. It only knows how
 * to POST packets and poll for incoming ones; the party and friend mark
 * features layer their own JSON on top.
 */
public final class BackendClient {
    public static final String DEFAULT_BACKEND_URL = "https://delta-backend-peach.vercel.app/api/delta";
    public static final String DEFAULT_BACKEND_TOKEN = "7ddd9c678114afbbab8eb9c3149e9461a0c3d2dcb5722a97ea0127445e55af7e";

    private static final Logger LOGGER = LoggerFactory.getLogger("communication");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
    private static final long POLL_INTERVAL_MS = 1_000L;
    private static final long ERROR_LOG_INTERVAL_MS = 5_000L;

    private final Consumer<Packet> receiver;
    private final Supplier<String> identity;
    private final HttpClient http;
    private final VercelTunnelProxy tunnel;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean pollInFlight = new AtomicBoolean();
    private final AtomicBoolean polling = new AtomicBoolean();
    private final AtomicInteger pollFailures = new AtomicInteger();
    private final String partySender = "party-" + UUID.randomUUID();

    private final String endpoint;
    private final String token;
    private volatile String cursor = "";
    private volatile boolean closed;
    private volatile long lastErrorLogAt;
    private ScheduledFuture<?> pollFuture;

    public BackendClient(Consumer<Packet> receiver, Supplier<String> identity) {
        this(DEFAULT_BACKEND_URL, DEFAULT_BACKEND_TOKEN, receiver, identity);
    }

    public BackendClient(String endpoint, String token, Consumer<Packet> receiver, Supplier<String> identity) {
        this.endpoint = normalize(endpoint);
        this.token = token == null ? "" : token.trim();
        this.receiver = Objects.requireNonNull(receiver);
        this.identity = Objects.requireNonNull(identity);

        VercelTunnelProxy localTunnel = null;
        try {
            localTunnel = new VercelTunnelProxy();
        } catch (IOException exception) {
            LOGGER.warn("Vercel tunnel unavailable: {}", exception.getMessage());
        }
        this.tunnel = localTunnel;

        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1);
        if (localTunnel != null) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(InetAddress.getLoopbackAddress(), localTunnel.port())));
        }
        this.http = builder.build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Communication-Vercel");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (this.closed) {
            return;
        }
        synchronized (this) {
            if (this.polling.get()) {
                return;
            }
            this.polling.set(true);
            this.pollFailures.set(0);
            this.pollFuture = this.scheduler.scheduleWithFixedDelay(this::poll, 250L, POLL_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void stop() {
        this.polling.set(false);
        if (this.pollFuture != null) {
            this.pollFuture.cancel(false);
            this.pollFuture = null;
        }
    }

    public CompletableFuture<Boolean> send(String id, String payloadJson) {
        if (this.closed || this.endpoint.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        JsonObject packet = new JsonObject();
        packet.addProperty("id", id);
        packet.add("payload", parsePayload(payloadJson));
        return post(packet, "party".equals(id) ? this.partySender : null);
    }

    private CompletableFuture<Boolean> post(JsonObject packet, String senderOverride) {
        HttpRequest request;
        try {
            request = requestBuilder(this.endpoint, senderOverride)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(packet.toString(), StandardCharsets.UTF_8))
                    .build();
        } catch (Exception exception) {
            logError("POST build", exception);
            return CompletableFuture.completedFuture(false);
        }
        return this.http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        logError("POST", throwable);
                        return false;
                    }
                    int status = response.statusCode();
                    if (status < 200 || status >= 300) {
                        logError("POST HTTP " + status, response.body());
                        return false;
                    }
                    handleResponse(response.body());
                    return true;
                });
    }

    private void poll() {
        if (this.closed || !this.polling.get() || this.endpoint.isEmpty()) {
            return;
        }
        if (!this.pollInFlight.compareAndSet(false, true)) {
            return;
        }
        HttpRequest request;
        try {
            request = requestBuilder(withCursor(this.endpoint, this.cursor), null).GET().build();
        } catch (Exception exception) {
            this.pollInFlight.set(false);
            logError("GET build", exception);
            return;
        }
        this.http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, throwable) -> {
                    try {
                        if (throwable != null) {
                            logError("GET", throwable);
                            return;
                        }
                        int status = response.statusCode();
                        if (status == 204) {
                            this.pollFailures.set(0);
                            return;
                        }
                        if (status == 404 || status == 405) {
                            if (this.pollFailures.incrementAndGet() >= 3) {
                                LOGGER.warn("Vercel GET not supported (HTTP {})", status);
                                stop();
                            }
                            return;
                        }
                        if (status < 200 || status >= 300) {
                            logError("GET HTTP " + status, response.body());
                            return;
                        }
                        this.pollFailures.set(0);
                        handleResponse(response.body());
                    } finally {
                        this.pollInFlight.set(false);
                    }
                });
    }

    private HttpRequest.Builder requestBuilder(String url, String senderOverride) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "Communication/1.0 (Minecraft 1.21.4)")
                .header("X-Delta-Client", "Communication");
        if (!this.token.isEmpty()) {
            builder.header("Authorization", "Bearer " + sanitizeHeader(this.token));
            builder.header("X-Delta-Token", sanitizeHeader(this.token));
        }
        String user = senderOverride == null ? this.identity.get() : senderOverride;
        user = sanitizeHeader(user);
        if (user.isEmpty()) {
            user = "anon";
        }
        builder.header("X-Delta-User", user);
        builder.header("X-Delta-Uid", user);
        builder.header("X-Delta-Hwid", user);
        return builder;
    }

    private void handleResponse(String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(body);
        } catch (Exception exception) {
            logError("response JSON", exception);
            return;
        }
        if (root.isJsonArray()) {
            handleArray(root.getAsJsonArray());
            return;
        }
        if (!root.isJsonObject()) {
            return;
        }
        JsonObject object = root.getAsJsonObject();
        updateCursor(object, "cursor");
        updateCursor(object, "nextCursor");
        updateCursor(object, "next_cursor");
        boolean handled = false;
        for (String key : List.of("packets", "messages", "data")) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonArray()) {
                handleArray(value.getAsJsonArray());
                handled = true;
            }
        }
        if (handled) {
            return;
        }
        if (object.has("id") && !object.get("id").isJsonNull() && object.has("payload")) {
            handlePacket(object);
        }
    }

    private void updateCursor(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value != null && !value.isJsonNull()) {
            this.cursor = value.getAsString();
        }
    }

    private void handleArray(JsonArray array) {
        for (JsonElement element : array) {
            if (element != null && element.isJsonObject()) {
                handlePacket(element.getAsJsonObject());
            }
        }
    }

    private void handlePacket(JsonObject packet) {
        JsonElement idElement = packet.get("id");
        if (idElement == null || idElement.isJsonNull() || idElement.getAsString().isBlank()) {
            return;
        }
        String id = idElement.getAsString();
        JsonElement payloadElement = packet.get("payload");
        String payload = payloadElement == null || payloadElement.isJsonNull()
                ? "{}"
                : payloadToJson(payloadElement);
        Packet decoded = new Packet(id, payload);
        MinecraftClient client = MinecraftClient.getInstance();
        Runnable task = () -> {
            try {
                this.receiver.accept(decoded);
            } catch (Throwable throwable) {
                LOGGER.warn("Packet handling failed: {}", id, throwable);
            }
        };
        if (client == null) {
            task.run();
        } else {
            client.execute(task);
        }
    }

    private JsonElement parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(payloadJson);
        } catch (Exception exception) {
            return new com.google.gson.JsonPrimitive(payloadJson);
        }
    }

    private static String payloadToJson(JsonElement payload) {
        if (payload.isJsonPrimitive() && payload.getAsJsonPrimitive().isString()) {
            return payload.getAsString();
        }
        return payload.toString();
    }

    private static String withCursor(String url, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return url;
        }
        char separator = url.contains("?") ? '&' : '?';
        return url + separator + "cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (!value.contains("://")) {
            value = "https://" + value;
        }
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getScheme() == null) {
                return "";
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return "";
            }
            return value;
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static String sanitizeHeader(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return sanitized.length() > 256 ? sanitized.substring(0, 256) : sanitized;
    }

    private void logError(String action, Throwable throwable) {
        String message = throwable == null ? "unknown"
                : throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        logError(action, message);
    }

    private void logError(String action, String details) {
        long now = System.currentTimeMillis();
        if (now - this.lastErrorLogAt < ERROR_LOG_INTERVAL_MS) {
            return;
        }
        this.lastErrorLogAt = now;
        String text = details == null ? "" : details.replace('\n', ' ').trim();
        if (text.length() > 300) {
            text = text.substring(0, 300) + "...";
        }
        LOGGER.warn("Vercel {}: {}", action, text);
    }

    public synchronized void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        stop();
        this.scheduler.shutdownNow();
        if (this.tunnel != null) {
            this.tunnel.close();
        }
    }

    public record Packet(String id, String payload) {
    }
}
