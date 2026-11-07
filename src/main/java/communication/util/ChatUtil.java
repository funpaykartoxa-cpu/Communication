package communication.util;

import communication.render.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ChatUtil {
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private ChatUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void send(String message) {
        if (MC.player == null) {
            return;
        }
        MutableText prefix = Text.literal("[Communication]").setStyle(Style.EMPTY.withColor(Theme.PRIMARY));
        MutableText separator = Text.literal(" » ").setStyle(Style.EMPTY.withColor(Theme.MUTED));
        MC.player.sendMessage(prefix.append(separator).append(parse(message)), false);
    }

    private static MutableText parse(String message) {
        MutableText result = Text.literal("");
        if (message == null || message.isEmpty()) {
            return result;
        }
        Style style = Style.EMPTY;
        StringBuilder chunk = new StringBuilder();
        for (int index = 0; index < message.length(); index++) {
            char character = message.charAt(index);
            if (character == '&' && index + 1 < message.length()) {
                Formatting formatting = Formatting.byCode(message.charAt(index + 1));
                if (formatting != null) {
                    if (chunk.length() > 0) {
                        result.append(Text.literal(chunk.toString()).setStyle(style));
                        chunk.setLength(0);
                    }
                    style = formatting.isColor()
                            ? style.withColor(formatting)
                            : style.withFormatting(formatting);
                    index++;
                    continue;
                }
            }
            chunk.append(character);
        }
        if (chunk.length() > 0) {
            result.append(Text.literal(chunk.toString()).setStyle(style));
        }
        return result;
    }
}
