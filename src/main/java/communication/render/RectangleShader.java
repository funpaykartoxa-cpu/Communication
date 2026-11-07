package communication.render;

import net.minecraft.client.gl.Uniform;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Vector4f;

public final class RectangleShader extends Shader {
    private Uniform size;
    private Uniform radius;
    private Uniform smoothness;
    private Uniform outlineWidth;

    public RectangleShader() {
        super(Identifier.of("communication", "core/rect/rect"), VertexFormats.POSITION_COLOR);
    }

    @Override
    protected void bindUniforms() {
        this.size = uniform("uSize");
        this.radius = uniform("uRadius");
        this.smoothness = uniform("uSmoothness");
        this.outlineWidth = uniform("uOutlineWidth");
    }

    public void size(float width, float height) {
        if (this.size != null) {
            this.size.set(width, height);
        }
    }

    public void radius(Vector4f value) {
        if (this.radius != null) {
            this.radius.set(value.x, value.z, value.w, value.y);
        }
    }

    public void smoothness(float value) {
        if (this.smoothness != null) {
            this.smoothness.set(value);
        }
    }

    public void outlineWidth(float value) {
        if (this.outlineWidth != null) {
            this.outlineWidth.set(value);
        }
    }
}
