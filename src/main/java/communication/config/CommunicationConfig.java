package communication.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Small JSON configuration owned by the Communication mod. The file lives in
 * {@code config/communication.json} and is written whenever the GUI changes a
 * value, so a crash never loses the current settings.
 */
public final class CommunicationConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("communication.json");

    public boolean enabled = true;
    public int partyMarkBind = -1;
    public int friendMarkBind = -1;
    public float partyMarkVolume = 0.8f;

    public static CommunicationConfig load() {
        try {
            if (Files.exists(PATH)) {
                String json = Files.readString(PATH, StandardCharsets.UTF_8);
                CommunicationConfig loaded = GSON.fromJson(json, CommunicationConfig.class);
                if (loaded != null) {
                    loaded.sanitize();
                    return loaded;
                }
            }
        } catch (Exception ignored) {
            // A damaged config must never stop the client from starting.
        }
        CommunicationConfig config = new CommunicationConfig();
        config.save();
        return config;
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Ignore write errors; the mod keeps working with in-memory values.
        }
    }

    private void sanitize() {
        if (partyMarkVolume < 0.0f) {
            partyMarkVolume = 0.0f;
        }
        if (partyMarkVolume > 1.0f) {
            partyMarkVolume = 1.0f;
        }
        if (partyMarkBind < -1) {
            partyMarkBind = -1;
        }
        if (friendMarkBind < -1) {
            friendMarkBind = -1;
        }
    }
}
