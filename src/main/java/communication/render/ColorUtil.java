package communication.render;

import java.awt.Color;

public final class ColorUtil {
    private ColorUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static float[] a(Color color) {
        return new float[]{color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f,
                color.getAlpha() / 255.0f};
    }

    public static float[] a(int color) {
        int[] components = b(color);
        return new float[]{components[0] / 255.0f, components[1] / 255.0f, components[2] / 255.0f,
                components[3] / 255.0f};
    }

    public static int[] b(int color) {
        return new int[]{(color >> 16) & 255, (color >> 8) & 255, color & 255, (color >> 24) & 255};
    }

    public static int makeGradient(int color1, int color2, float position, float totalWidth, float time, float offset) {
        float gradientLength = 18.0f / offset;
        float wavePosition = (time + (position / (totalWidth * gradientLength))) % 1.0f;
        float factor = (((float) Math.sin(wavePosition * Math.PI * 2.0f)) * 0.5f) + 0.5f;
        int a1 = (color1 >> 24) & 255;
        int r1 = (color1 >> 16) & 255;
        int g1 = (color1 >> 8) & 255;
        int b1 = color1 & 255;
        int a2 = (color2 >> 24) & 255;
        int r2 = (color2 >> 16) & 255;
        int g2 = (color2 >> 8) & 255;
        int b2 = color2 & 255;
        int a = (int) (a1 + ((a2 - a1) * factor));
        int r = (int) (r1 + ((r2 - r1) * factor));
        int g = (int) (g1 + ((g2 - g1) * factor));
        int b = (int) (b1 + ((b2 - b1) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int applyAlphaToColor(int color, float alpha) {
        return (color & 16777215) | (Math.round(alpha * 255.0f) << 24);
    }

    public static int combineColorWithAlpha(int color, int alpha) {
        return (color & 16777215) | (alpha << 24);
    }

    public static int convertToARGB(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int lerpColor(int from, int to, float t) {
        int[] f = b(from);
        int[] tArr = b(to);
        return convertToARGB((int) (f[0] + ((tArr[0] - f[0]) * t)), (int) (f[1] + ((tArr[1] - f[1]) * t)),
                (int) (f[2] + ((tArr[2] - f[2]) * t)), (int) (f[3] + ((tArr[3] - f[3]) * t)));
    }

    public static int darken(int color, float factor) {
        float[] rgb = a(color);
        return convertToARGB((int) (rgb[0] * 255.0f * factor), (int) (rgb[1] * 255.0f * factor),
                (int) (rgb[2] * 255.0f * factor), (int) (rgb[3] * 255.0f));
    }
}
