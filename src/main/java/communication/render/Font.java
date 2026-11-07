package communication.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MSDF font renderer ported from the Delta client. Keeps the original smooth
 * text shader and glyph atlas so the overlay and menus look identical.
 */
public final class Font {
    private final ShaderProgramKey shaderKey = new ShaderProgramKey(
            Identifier.of("communication", "core/text/text"),
            VertexFormats.POSITION_TEXTURE_COLOR, Defines.EMPTY);
    private final String fontName;
    private final AbstractTexture fontTexture;
    private final FontData.AtlasData atlasData;
    private final FontData.MetricsData metricsData;
    private final Map<Integer, MsdfGlyph> glyphs;
    private final Map<Integer, Map<Integer, Float>> kernings;

    public Font(String name, AbstractTexture texture, FontData.AtlasData atlas, FontData.MetricsData metrics,
                Map<Integer, MsdfGlyph> glyphs, Map<Integer, Map<Integer, Float>> kernings) {
        this.fontName = name;
        this.fontTexture = texture;
        this.atlasData = atlas;
        this.metricsData = metrics;
        this.glyphs = glyphs;
        this.kernings = kernings;
    }

    public static FontBuilder builder() {
        return new FontBuilder();
    }

    public String name() {
        return this.fontName;
    }

    public void draw(MatrixStack matrices, String text, float x, float y, float size, int color) {
        draw(matrices, text, x, y, size, color, 0.0f);
    }

    public void draw(MatrixStack matrices, String text, float x, float y, float size, int color, float thickness) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        prepare(0.0f, thickness, 0.5f, -1, -1.0f, -1.0f);
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        float adjustedThickness = thickness * 0.5f * size;
        float baselineY = y + (this.metricsData.baselineHeight() * size);
        boolean hasGlyphs = drawString(matrix, builder, text, size, adjustedThickness, 0.0f, x, baselineY, 0.0f, color);
        if (hasGlyphs) {
            flush(builder);
        }
    }

    public void draw(MatrixStack matrices, Text text, float x, float y, float size) {
        draw(matrices, text, x, y, size, 1.0f, 0.0f, 0.0f, 0.0f, -1, 0.0f);
    }

    public void draw(MatrixStack matrices, Text text, float x, float y, float size, float alpha) {
        draw(matrices, text, x, y, size, alpha, 0.0f, 0.0f, 0.0f, -1, 0.0f);
    }

    public void draw(MatrixStack matrices, Text text, float x, float y, float size, float alpha,
                     float thickness, float smoothness, float spacing, int outlineColor, float outlineThickness) {
        if (text == null) {
            return;
        }
        try {
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            prepare(outlineThickness, thickness, smoothness, outlineColor, -1.0f, -1.0f);
            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            float adjustedThickness = (thickness + (outlineThickness * 0.5f)) * 0.5f * size;
            float baselineY = y + (this.metricsData.baselineHeight() * size);
            boolean hasGlyphs = drawColored(matrix, builder, collect(text), size, alpha, adjustedThickness, spacing, x,
                    baselineY, 0.0f);
            if (hasGlyphs) {
                flush(builder);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public void drawCentered(MatrixStack matrices, String text, float centerX, float y, float size, int color) {
        draw(matrices, text, centerX - (width(text, size) / 2.0f), y, size, color);
    }

    public void drawRight(MatrixStack matrices, String text, float right, float y, float size, int color) {
        draw(matrices, text, right - width(text, size), y, size, color);
    }

    public float width(Text text, float size) {
        return width(collect(text), size);
    }

    public float width(String text, float size) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        int previous = -1;
        float width = 0.0f;
        for (int index = 0; index < text.length(); index++) {
            int codePoint = text.charAt(index);
            MsdfGlyph glyph = this.glyphs.get(codePoint);
            if (glyph != null) {
                width += kerning(previous, codePoint, size) + glyph.advance(size);
                previous = codePoint;
            }
        }
        return width;
    }

    public float textY(String text, float size, float centerY) {
        if (text == null || text.isEmpty()) {
            return centerY - (size / 2.0f);
        }
        MsdfGlyph glyph = this.glyphs.get((int) text.charAt(0));
        if (glyph == null) {
            return centerY - (size / 2.0f);
        }
        float inkCenter = ((this.metricsData.baselineHeight() - glyph.planeTop()) + (glyph.planeHeight() / 2.0f)) * size;
        return centerY - inkCenter;
    }

    private List<ColoredGlyph> collect(Text text) {
        List<ColoredGlyph> result = new ArrayList<>();
        text.visit((style, string) -> {
            if (string == null || string.isEmpty()) {
                return Optional.empty();
            }
            int color = style.getColor() != null ? style.getColor().getRgb() | 0xFF000000 : 0xFFFFFFFF;
            for (int index = 0; index < string.length(); index++) {
                char character = string.charAt(index);
                if (this.glyphs.containsKey((int) character)) {
                    result.add(new ColoredGlyph(character, color));
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private boolean drawString(Matrix4f matrix, BufferBuilder builder, String text, float size, float thickness,
                               float spacing, float x, float y, float z, int color) {
        int previous = -1;
        boolean hasGlyphs = false;
        for (int index = 0; index < text.length(); index++) {
            int codePoint = text.charAt(index);
            MsdfGlyph glyph = this.glyphs.get(codePoint);
            if (glyph == null) {
                continue;
            }
            hasGlyphs = true;
            float x2 = x + kerning(previous, codePoint, size);
            float advance = glyph.draw(matrix, builder, size, x2, y, z, color);
            x = x2 + advance + thickness + spacing;
            previous = codePoint;
        }
        return hasGlyphs;
    }

    private boolean drawColored(Matrix4f matrix, BufferBuilder builder, List<ColoredGlyph> text, float size,
                                float alpha, float thickness, float spacing, float x, float y, float z) {
        int previous = -1;
        boolean hasGlyphs = false;
        for (int index = 0; index < text.size(); index++) {
            ColoredGlyph glyphData = text.get(index);
            MsdfGlyph glyph = this.glyphs.get((int) glyphData.character());
            if (glyph == null) {
                continue;
            }
            hasGlyphs = true;
            float x2 = x + kerning(previous, glyphData.character(), size);
            float advance = glyph.draw(matrix, builder, size, x2, y, z,
                    ColorUtil.applyAlphaToColor(glyphData.color(), alpha));
            if (index < text.size() - 1) {
                advance += thickness + spacing;
            }
            x = x2 + advance;
            previous = glyphData.character();
        }
        return hasGlyphs;
    }

    private float width(List<ColoredGlyph> glyphs, float size) {
        int previous = -1;
        float width = 0.0f;
        for (ColoredGlyph colored : glyphs) {
            MsdfGlyph glyph = this.glyphs.get((int) colored.character());
            if (glyph != null) {
                width += kerning(previous, colored.character(), size) + glyph.advance(size);
                previous = colored.character();
            }
        }
        return width;
    }

    private float kerning(int previous, int current, float size) {
        Map<Integer, Float> values = this.kernings.get(previous);
        if (values == null) {
            return 0.0f;
        }
        return values.getOrDefault(current, 0.0f) * size;
    }

    private void prepare(float outlineThickness, float thickness, float smoothness, int outlineColor,
                         float fadeStart, float fadeEnd) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, this.fontTexture.getGlId());
        ShaderProgram shader = RenderSystem.setShader(this.shaderKey);
        if (shader == null) {
            return;
        }
        shader.getUniform("uRange").set(this.atlasData.range());
        shader.getUniform("uThickness").set(thickness);
        shader.getUniform("uSmoothness").set(smoothness);
        boolean outline = outlineThickness > 0.0f;
        shader.getUniform("uOutline").set(outline ? 1 : 0);
        if (outline) {
            shader.getUniform("uOutlineThickness").set(outlineThickness);
            float[] components = ColorUtil.a(outlineColor);
            shader.getUniform("uOutlineColor").set(components[0], components[1], components[2], components[3]);
        }
        boolean fade = fadeEnd > fadeStart;
        shader.getUniform("uFadeEnabled").set(fade ? 1 : 0);
        if (fade) {
            shader.getUniform("uFadeStart").set(fadeStart);
            shader.getUniform("uFadeEnd").set(fadeEnd);
        }
    }

    private void flush(BufferBuilder builder) {
        BufferRenderer.drawWithGlobalProgram(builder.end());
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private record ColoredGlyph(char character, int color) {
    }
}
