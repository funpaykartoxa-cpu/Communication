package communication.render;

import com.google.gson.Gson;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class FontBuilder {
    private final Gson gson = new Gson();
    private Identifier fontJsonId;
    private Identifier fontTextureId;
    private String fontName;

    public FontBuilder name(String fontName) {
        this.fontName = fontName;
        this.fontJsonId = Identifier.of("communication", "fonts/" + fontName + ".json");
        this.fontTextureId = Identifier.of("communication", "fonts/" + fontName + ".png");
        return this;
    }

    public Font build() {
        FontData data = readData();
        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(this.fontTextureId);
        RenderSystem.recordRenderCall(() -> texture.setFilter(true, false));
        Map<Integer, MsdfGlyph> glyphs = new HashMap<>();
        for (FontData.GlyphData glyph : data.glyphs()) {
            glyphs.put(glyph.unicode(), new MsdfGlyph(glyph, data.atlas().width(), data.atlas().height()));
        }
        Map<Integer, Map<Integer, Float>> kernings = new HashMap<>();
        if (data.kernings() != null) {
            for (FontData.KerningData kerning : data.kernings()) {
                kernings.computeIfAbsent(kerning.leftChar(), key -> new HashMap<>())
                        .put(kerning.rightChar(), kerning.advance());
            }
        }
        return new Font(this.fontName, texture, data.atlas(), data.metrics(), glyphs, kernings);
    }

    private FontData readData() {
        try (InputStream input = MinecraftClient.getInstance().getResourceManager().open(this.fontJsonId);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            FontData data = this.gson.fromJson(reader.lines().collect(Collectors.joining("\n")), FontData.class);
            if (data == null) {
                throw new IllegalStateException("Invalid font data: " + this.fontJsonId);
            }
            return data;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read font: " + this.fontJsonId, exception);
        }
    }
}
