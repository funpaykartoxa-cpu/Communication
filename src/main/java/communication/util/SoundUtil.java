package communication.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SoundUtil {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Communication-Sound");
        thread.setDaemon(true);
        return thread;
    });

    private SoundUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Plays one of the bundled WAV files independently of the Minecraft sound
     * engine, exactly like the original client did for party marks.
     */
    public static void play(String filename, float volume) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (filename == null || filename.isEmpty() || client.getResourceManager() == null) {
            return;
        }
        EXECUTOR.execute(() -> {
            try (AudioInputStream stream = AudioSystem.getAudioInputStream(new BufferedInputStream(
                    client.getResourceManager().open(Identifier.of("communication", "sounds/" + filename))))) {
                Clip clip = AudioSystem.getClip();
                clip.open(stream);
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(),
                            (float) (20.0d * Math.log10(Math.max(volume, 1.0E-4f))))));
                }
                clip.start();
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });
            } catch (Exception ignored) {
                // Missing audio device or resource: silently ignore.
            }
        });
    }
}
