package dev.orbis.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Flat HUD map: one native texel per block, with smooth GPU scrolling between updates. */
final class AtlasMinimapTexture implements AutoCloseable {
    private final Identifier id = Identifier.fromNamespaceAndPath("orbis", "minimap_tiles");
    private final AtlasMinimapCache cache = new AtlasMinimapCache();
    private DynamicTexture texture;
    private AtlasMinimapCache.Frame uploaded;

    void draw(GuiGraphicsExtractor graphics, AtlasSnapshot data, double x, double z, double span,
            int left, int top, int size) {
        var frame = cache.update(data, x, z, span);
        if (frame == null) return;
        int resolution = frame.window().size();
        if (texture == null || texture.getPixels().getWidth() != resolution) {
            if (texture != null) Minecraft.getInstance().getTextureManager().release(id);
            texture = new DynamicTexture(() -> "Orbis minimap terrain", new NativeImage(resolution, resolution, false));
            Minecraft.getInstance().getTextureManager().register(id, texture);
            uploaded = null;
        }
        if (uploaded != frame) {
            var image = texture.getPixels();
            for (int py = 0; py < resolution; py++) for (int px = 0; px < resolution; px++)
                image.setPixel(px, py, frame.pixels()[py * resolution + px]);
            texture.upload();
            uploaded = frame;
        }
        var window = frame.window();
        graphics.blit(id, left, top, left + size, top + size,
                window.u(x - span / 2), window.u(x + span / 2), window.v(z - span / 2), window.v(z + span / 2));
    }

    @Override public void close() {
        cache.close();
        if (texture != null) Minecraft.getInstance().getTextureManager().release(id);
        texture = null; uploaded = null;
    }
}
