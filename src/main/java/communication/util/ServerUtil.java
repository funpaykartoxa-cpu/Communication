package communication.util;

import communication.mixin.PlayerListHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerUtil {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final Pattern ANARCHY = Pattern.compile("(?iu)анархия\\s*[-–—]?\\s*(\\d+)");

    private ServerUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static String header() {
        try {
            if (MC.inGameHud == null || MC.inGameHud.getPlayerListHud() == null) {
                return "";
            }
            Text header = ((PlayerListHudAccessor) MC.inGameHud.getPlayerListHud()).getHeader();
            return header == null ? "" : header.getString();
        } catch (Exception exception) {
            return "";
        }
    }

    public static String serverAddress() {
        try {
            return MC.getCurrentServerEntry() == null ? "" : MC.getCurrentServerEntry().address;
        } catch (Exception exception) {
            return "";
        }
    }

    public static String dimension() {
        return MC.world == null ? "" : MC.world.getRegistryKey().getValue().toString();
    }

    public static int anarchy() {
        try {
            String header = header();
            if (header == null || !header.contains("Анархия-")) {
                return -1;
            }
            Matcher matcher = ANARCHY.matcher(header);
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
        } catch (Exception exception) {
            return -1;
        }
    }

    public static boolean sameServer(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.equalsIgnoreCase(right)) {
            return true;
        }
        String leftDomain = baseDomain(left);
        String rightDomain = baseDomain(right);
        return !leftDomain.isEmpty() && leftDomain.equals(rightDomain);
    }

    public static String baseDomain(String address) {
        String value = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "";
        }
        int port = value.indexOf(':');
        if (port > 0) {
            value = value.substring(0, port);
        }
        if (value.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")) {
            return value;
        }
        String[] parts = value.split("\\.");
        if (parts.length <= 2) {
            return value;
        }
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }
}
