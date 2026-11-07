package communication.render;

/**
 * Shared palette. Colors are ARGB and mirror the dark Delta theme, but the
 * accent is kept fully opaque so small labels stay readable on the overlay.
 */
public final class Theme {
    public static final int PRIMARY = 0xFF859CFF;
    public static final int PRIMARY_DARK = 0xFF5F74D8;
    public static final int PRIMARY_SOFT = 0x33859CFF;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFF7C8296;
    public static final int MUTED_DARK = 0xFF434651;
    public static final int BACKGROUND_HUD = 0xEB0B0B16;
    public static final int BACKGROUND_GUI = 0xF5080A12;
    public static final int PANEL = 0xFF0D0F1A;
    public static final int PANEL_RAISED = 0xFF141726;
    public static final int PANEL_HOVER = 0xFF1B1F30;
    public static final int OUTLINE = 0x22FFFFFF;
    public static final int OUTLINE_STRONG = 0x44FFFFFF;
    public static final int SUCCESS = 0xFF5FDC73;
    public static final int WARNING = 0xFFF5CD4B;
    public static final int DANGER = 0xFFF55A5A;
    public static final int SHADOW = 0x96000000;

    private Theme() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
