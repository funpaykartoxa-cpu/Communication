package communication.friend;

import communication.CommunicationMod;
import communication.config.CommunicationConfig;
import communication.network.BackendClient;
import communication.render.Fonts;
import communication.render.RoundedRenderer;
import communication.render.Theme;
import communication.util.ProjectUtil;
import communication.util.ServerUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Friend marks use the public {@code friend} packet of the shared backend. The
 * packet carries a world position plus the sender name; every client that
 * receives it shows a short-lived label above that position. There is no
 * friend list, so any mark that arrives is displayed, matching the original
 * Communication module.
 */
public final class FriendMarkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("communication");
    private static final String PACKET_ID = "friend";
    private static final long MARK_DURATION_MS = 5_000L;
    private static final float MARKER_TEXT_SIZE = 6.25f;
    private static final float MARKER_PILL_HEIGHT = 12.0f;
    private static final int SHADOW_COLOR = 0x96000000;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final List<FriendMark> marks = new ArrayList<>();

    public boolean sendMark() {
        CommunicationConfig config = config();
        if (config == null || !config.enabled || this.mc.player == null || this.mc.world == null) {
            return false;
        }
        Vec3d position = this.mc.player.getPos();
        JsonObject packet = new JsonObject();
        packet.addProperty("type", "mark");
        packet.addProperty("minecraft", this.mc.getSession().getUsername());
        packet.addProperty("server", ServerUtil.serverAddress());
        packet.addProperty("anarchy", ServerUtil.anarchy());
        packet.addProperty("dimension", ServerUtil.dimension());
        JsonObject pos = new JsonObject();
        pos.addProperty("x", position.x);
        pos.addProperty("y", position.y);
        pos.addProperty("z", position.z);
        packet.add("pos", pos);

        CommunicationMod mod = CommunicationMod.get();
        BackendClient backend = mod == null ? null : mod.backend();
        if (backend == null) {
            return false;
        }
        backend.send(PACKET_ID, packet.toString());
        LOGGER.info("Friend mark sent: {}", String.format(Locale.US, "%.1f, %.1f, %.1f",
                position.x, position.y, position.z));
        return true;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        this.marks.removeIf(mark -> mark.expiresAt() <= now);
    }

    public void onPacket(String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        try {
            JsonElement element = JsonParser.parseString(payload);
            if (!element.isJsonObject()) {
                return;
            }
            JsonObject object = element.getAsJsonObject();
            if (!"mark".equals(string(object, "type"))) {
                return;
            }
            JsonElement posElement = object.get("pos");
            if (posElement == null || !posElement.isJsonObject()) {
                return;
            }
            JsonObject pos = posElement.getAsJsonObject();
            if (!pos.has("x") || !pos.has("y") || !pos.has("z")) {
                return;
            }
            String login = string(object, "minecraft");
            if (login.isBlank()) {
                return;
            }
            int anarchy = integer(object, "anarchy", -1);
            int currentAnarchy = ServerUtil.anarchy();
            if (anarchy >= 0 && currentAnarchy >= 0 && anarchy != currentAnarchy) {
                return;
            }
            String dimension = string(object, "dimension");
            if (!dimension.isBlank() && this.mc.world != null
                    && !dimension.equals(this.mc.world.getRegistryKey().getValue().toString())) {
                return;
            }
            double x = pos.get("x").getAsDouble();
            double y = pos.get("y").getAsDouble();
            double z = pos.get("z").getAsDouble();
            String name = login;
            this.marks.removeIf(mark -> mark.name().equalsIgnoreCase(name));
            this.marks.add(new FriendMark(name, x, y, z, System.currentTimeMillis() + MARK_DURATION_MS));
            LOGGER.info("Friend mark from {} at {}, {}, {}", name, x, y, z);
        } catch (Exception ignored) {
            // Malformed packets must not break the HUD.
        }
    }

    public void renderLabels(DrawContext context) {
        CommunicationConfig config = config();
        if (config == null || !config.enabled || this.mc.player == null || this.mc.world == null) {
            return;
        }
        if (this.mc.options.hudHidden || this.mc.player.isSpectator()) {
            return;
        }
        long now = System.currentTimeMillis();
        this.marks.removeIf(mark -> mark.expiresAt() <= now);
        for (FriendMark mark : this.marks) {
            drawLabel(context, mark);
        }
    }

    private void drawLabel(DrawContext context, FriendMark mark) {
        Vector2f screen = ProjectUtil.project(mark.x(), mark.y(), mark.z());
        if (!ProjectUtil.isOnScreen(screen)) {
            return;
        }
        double distance = this.mc.player.getEyePos().distanceTo(new Vec3d(mark.x(), mark.y(), mark.z()));
        String suffix = String.format(Locale.US, "%.1fм", distance);
        Text text = Text.literal(mark.name().toUpperCase(Locale.ROOT))
                .append(Text.literal("  /  ").setStyle(Style.EMPTY.withColor(Theme.PRIMARY)))
                .append(Text.literal(suffix));
        float width = 6.0f + 8.0f + 2.5f + Fonts.gt().width(text, MARKER_TEXT_SIZE);
        float x = screen.x() - (width / 2.0f);
        float y = screen.y() - (MARKER_PILL_HEIGHT / 2.0f);
        float textX = x + 2.5f + 8.0f + 2.5f;
        float textY = y + ((MARKER_PILL_HEIGHT - MARKER_TEXT_SIZE) / 2.0f) - 0.5f;

        RoundedRenderer.fill(context.getMatrices(), x, y, width, MARKER_PILL_HEIGHT, 3.5f, 0xEB0B0B16);
        PlayerSkinDrawer.draw(context, skin(mark.name()), (int) (x + 2.5f), (int) (y + 2.0f), 8);
        Fonts.gt().draw(context.getMatrices(), text, textX + 0.75f, textY + 0.75f, MARKER_TEXT_SIZE, SHADOW_COLOR);
        Fonts.gt().draw(context.getMatrices(), text, textX, textY, MARKER_TEXT_SIZE);
    }

    private static SkinTextures skin(String name) {
        try {
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            return DefaultSkinHelper.getSkinTextures(uuid);
        } catch (Exception ignored) {
            return DefaultSkinHelper.getSkinTextures(new UUID(0L, 0L));
        }
    }

    private static CommunicationConfig config() {
        CommunicationMod mod = CommunicationMod.get();
        return mod == null ? null : mod.config();
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private static int integer(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception exception) {
            return fallback;
        }
    }

    private record FriendMark(String name, double x, double y, double z, long expiresAt) {
    }
}
