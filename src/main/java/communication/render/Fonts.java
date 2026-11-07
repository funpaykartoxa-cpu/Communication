package communication.render;

/**
 * Lazily created font instances. Loading them on first use avoids touching the
 * texture manager before the client finished its initial resource reload.
 */
public final class Fonts {
    private static Font gt;
    private static Font icons;

    private Fonts() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static Font gt() {
        if (gt == null) {
            gt = Font.builder().name("gt_regular").build();
        }
        return gt;
    }

    public static Font icons() {
        if (icons == null) {
            icons = Font.builder().name("icons").build();
        }
        return icons;
    }
}
