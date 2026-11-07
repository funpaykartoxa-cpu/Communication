package communication.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Rounded rectangles drawn with the original Delta rectangle shader. Used by
 * the HUD pills and the new compact settings screen.
 */
public final class RoundedRenderer {
    private static final RectangleShader SHADER = new RectangleShader();
    private static final float PADDING = 0.8f;

    private RoundedRenderer() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void fill(MatrixStack matrices, float x, float y, float width, float height, float radius, int color) {
        fill(matrices, x, y, width, height, new Vector4f(radius, radius, radius, radius), color);
    }

    public static void fill(MatrixStack matrices, float x, float y, float width, float height, Vector4f radius, int color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float drawX = x - (PADDING / 2.0f);
        float drawY = y - (PADDING / 2.0f);
        float drawWidth = width + PADDING;
        float drawHeight = height + PADDING;
        begin();
        SHADER.use();
        SHADER.size(width, height);
        SHADER.radius(radius);
        SHADER.smoothness(0.8f);
        SHADER.outlineWidth(0.0f);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        quad(buffer, matrix, drawX, drawY, drawWidth, drawHeight, color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        end();
    }

    public static void outline(MatrixStack matrices, float x, float y, float width, float height, float radius,
                               float outlineWidth, int color) {
        outline(matrices, x, y, width, height, new Vector4f(radius, radius, radius, radius), outlineWidth, color);
    }

    public static void outline(MatrixStack matrices, float x, float y, float width, float height, Vector4f radius,
                               float outlineWidth, int color) {
        float half = outlineWidth * 0.5f;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float drawX = (x - half) - (PADDING / 2.0f);
        float drawY = (y - half) - (PADDING / 2.0f);
        float drawWidth = width + outlineWidth + PADDING;
        float drawHeight = height + outlineWidth + PADDING;
        begin();
        SHADER.use();
        SHADER.size(width + outlineWidth, height + outlineWidth);
        SHADER.radius(new Vector4f(radius.x + half, radius.y + half, radius.z + half, radius.w + half));
        SHADER.smoothness(0.8f);
        SHADER.outlineWidth(outlineWidth);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        quad(buffer, matrix, drawX, drawY, drawWidth, drawHeight, color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        end();
    }

    private static void begin() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
    }

    private static void end() {
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix, float x, float y, float width, float height, int color) {
        buffer.vertex(matrix, x, y, 0.0f).color(color);
        buffer.vertex(matrix, x, y + height, 0.0f).color(color);
        buffer.vertex(matrix, x + width, y + height, 0.0f).color(color);
        buffer.vertex(matrix, x + width, y, 0.0f).color(color);
    }
}
