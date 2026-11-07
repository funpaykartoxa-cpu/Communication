package communication.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.Uniform;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.Identifier;

public abstract class Shader {
    protected final ShaderProgramKey key;
    protected ShaderProgram program;

    public Shader(Identifier identifier, VertexFormat vertexFormat) {
        this.key = new ShaderProgramKey(identifier, vertexFormat, Defines.EMPTY);
    }

    protected abstract void bindUniforms();

    public ShaderProgramKey key() {
        return this.key;
    }

    public ShaderProgram program() {
        return this.program;
    }

    public void use() {
        this.program = RenderSystem.setShader(this.key);
        bindUniforms();
    }

    protected Uniform uniform(String name) {
        return this.program == null ? null : this.program.getUniform(name);
    }
}
