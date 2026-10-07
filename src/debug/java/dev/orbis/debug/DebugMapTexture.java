package dev.orbis.debug;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** One cached texture per open screen. Idle frames reuse it; statistics cannot invalidate it. */
final class DebugMapTexture implements AutoCloseable {
    private static int nextId;
    private final Identifier id = Identifier.fromNamespaceAndPath("orbis", "atlas_" + nextId++);
    private DynamicTexture texture;
    private DebugMapRaster raster;
    private DebugMapKey previousKey;
    private int width, height, uploads;
    private double updateMillis;
    private long lastUpload;
    private final AtlasTerrainBackdrop backdrop = new AtlasTerrainBackdrop();
    private long previousBackdropVersion = -1;

    void draw(GuiGraphicsExtractor g, DebugSnapshot data, DebugMapRaster.View view, int x, int y) {
        int limit = view.globe() ? 768 : 1536;
        double factor = Math.min(Minecraft.getInstance().getWindow().getGuiScale(),
                limit / (double) Math.max(view.width(), view.height()));
        int w = Math.max(1, (int) (view.width() * factor)), h = Math.max(1, (int) (view.height() * factor));
        if (texture == null || w != width || h != height) {
            close();
            width = w; height = h;
            raster = new DebugMapRaster(w, h);
            texture = new DynamicTexture(() -> "Orbis debug atlas", new NativeImage(w, h, false));
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
        // Coalesce input events; expensive globe reprojection is capped at 30 updates/s.
        var key = DebugMapKey.of(data, view);
        if (previousKey == null || System.nanoTime() - lastUpload >= 33_333_333L) {
            int[] archived = backdrop.update(data, key.view(), w, h);
            if (!key.equals(previousKey) || previousBackdropVersion != backdrop.version()) {
                long start = System.nanoTime();
                int[] pixels = raster.render(data, view, archived);
                NativeImage target = texture.getPixels();
                for (int py = 0; py < height; py++) for (int px = 0; px < width; px++) target.setPixel(px, py, pixels[py * width + px]);
                texture.upload();
                previousKey = key;
                previousBackdropVersion = backdrop.version();
                uploads++;
                lastUpload = System.nanoTime();
                updateMillis = (System.nanoTime() - start) / 1_000_000.0;
            }
        }
        // This overload takes opposite corners, not a width and height.
        g.blit(id, x, y, x + view.width(), y + view.height(), 0, 1, 0, 1);
    }
    int uploads() { return uploads; }
    double updateMillis() { return updateMillis; }
    String resolution() { return width + " x " + height; }
    @Override public void close() {
        backdrop.close(); previousBackdropVersion = -1;
        if (texture != null) Minecraft.getInstance().getTextureManager().release(id);
        texture = null; raster = null; previousKey = null;
    }
}
