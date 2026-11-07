package communication.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local HTTP CONNECT tunnel for the Vercel backend. The ISP sometimes blocks
 * the DNS-resolved Vercel addresses while other edge IPs remain reachable, so
 * this keeps TLS end-to-end but dials a known edge directly.
 */
final class VercelTunnelProxy implements AutoCloseable {
    private static final String HOST = "delta-backend-peach.vercel.app";
    private static final int PORT = 443;
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final String[] EDGE_IPS = {
            "64.239.123.129",
            "64.239.123.1",
            "64.239.123.65",
            "64.29.17.1",
            "76.76.21.61",
            "76.76.21.98",
            "76.76.21.142"
    };

    private final ServerSocket server;
    private final AtomicBoolean closed = new AtomicBoolean();

    VercelTunnelProxy() throws IOException {
        this.server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(this::acceptLoop, "Communication-Vercel-Tunnel");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    int port() {
        return this.server.getLocalPort();
    }

    private void acceptLoop() {
        while (!this.closed.get()) {
            try {
                Socket client = this.server.accept();
                Thread worker = new Thread(() -> handle(client), "Communication-Vercel-Tunnel-Conn");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException ignored) {
                // Server closed or a transient accept failure.
            }
        }
    }

    private void handle(Socket client) {
        try (client) {
            client.setSoTimeout(15_000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(),
                    StandardCharsets.ISO_8859_1));
            String requestLine = reader.readLine();
            if (requestLine == null) {
                return;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2 || !"CONNECT".equalsIgnoreCase(parts[0])) {
                write(client, "HTTP/1.1 405 Method Not Allowed\r\n\r\n");
                return;
            }
            String authority = parts[1];
            String host = authority;
            int port = PORT;
            int colon = authority.lastIndexOf(':');
            if (colon > 0) {
                host = authority.substring(0, colon);
                try {
                    port = Integer.parseInt(authority.substring(colon + 1));
                } catch (NumberFormatException ignored) {
                    port = PORT;
                }
            }
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Consume CONNECT headers.
            }
            Socket upstream = connect(host, port);
            if (upstream == null) {
                write(client, "HTTP/1.1 502 Bad Gateway\r\n\r\n");
                return;
            }
            try (upstream) {
                write(client, "HTTP/1.1 200 Connection Established\r\n\r\n");
                client.setSoTimeout(0);
                Thread pump = new Thread(() -> pump(upstream, client), "Communication-Vercel-Tunnel-Up");
                pump.setDaemon(true);
                pump.start();
                pump(client, upstream);
            }
        } catch (Exception ignored) {
            // Next poll retries.
        }
    }

    private Socket connect(String host, int port) {
        if (HOST.equalsIgnoreCase(host) && port == PORT) {
            for (String ip : EDGE_IPS) {
                try {
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(InetAddress.getByName(ip), port), CONNECT_TIMEOUT_MS);
                    return socket;
                } catch (IOException ignored) {
                    // Try the next edge.
                }
            }
        }
        try {
            return new Socket(host, port);
        } catch (IOException exception) {
            return null;
        }
    }

    private static void pump(Socket from, Socket to) {
        try {
            InputStream input = from.getInputStream();
            OutputStream output = to.getOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                output.flush();
            }
        } catch (IOException ignored) {
            // Connection closed.
        } finally {
            try {
                to.shutdownOutput();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
    }

    private static void write(Socket socket, String text) throws IOException {
        OutputStream output = socket.getOutputStream();
        output.write(text.getBytes(StandardCharsets.ISO_8859_1));
        output.flush();
    }

    @Override
    public void close() {
        this.closed.set(true);
        try {
            this.server.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }
}
