package dev.orbis.atlas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Compact north-up terrain map, or a globe oriented toward the live player. */
final class OrbisMinimap implements AutoCloseable {
    // The corner globe turns slowly with the player; a few updates a second keep it smooth without busy work.
    private final AtlasMapTexture texture = new AtlasMapTexture(20);
    private final AtlasMinimapTexture flatTexture = new AtlasMinimapTexture();

    void draw(GuiGraphicsExtractor g, AtlasSnapshot data) {
        Minecraft client = Minecraft.getInstance();
        if (!AtlasPreferences.visible() || client.gui.hud.isHidden() || client.player == null || client.level == null || client.gui.screen() != null || data == null
                || !data.dimension().equals(client.level.dimension().identifier().toString())) return;
        int size = fittedSize(g.guiWidth(), g.guiHeight(), AtlasPreferences.size());
        int corner = AtlasPreferences.corner();
        int left = corner % 2 == 0 ? g.guiWidth() - size - 16 : 16;
        int top = corner < 2 ? 16 : g.guiHeight() - size - 78;
        boolean globe = AtlasPreferences.globe(data.dimension()) && data.wrapped();
        // Follow the exact player position. Globe texture updates are already throttled.
        double x = client.player.getX(), z = client.player.getZ();
        double yaw = -((x - data.minX()) / data.span() - .5) * Math.PI * 2;
        double pitch = (.5 - (z - data.minZ()) / data.span()) * Math.PI;
        double scale = size / (double) Math.min(256 / AtlasPreferences.minimapZoom(), data.span());
        if (!globe && data.wrapped()) {
            // Centred on the player even beside a seam: the map continues with the other side of the world.
            x = AtlasMapMath.canonical(x, data.minX(), data.span());
            z = AtlasMapMath.canonical(z, data.minZ(), data.span());
        }
        if (!globe) {
            g.fill(left - 3, top - 3, left + size + 3, top + size + 3, 0xff202020);
            g.outline(left - 2, top - 2, size + 4, size + 4, 0xffaaaaaa);
        }
        if (globe) texture.draw(g, data, new AtlasMapRaster.View(size, size, x, z, 1,
                true, yaw, pitch, false, false), left, top);
        else flatTexture.draw(g, data, x, z, size / scale, left, top, size);
        g.enableScissor(left, top, left + size, top + size);
        var shown = globe ? texture.displayedView() : new AtlasMapRaster.View(size, size, x, z, scale,
                false, 0, 0, false, false);
        if (shown != null) AtlasPlayersClient.draw(g, data, shown, left, top, 0, 0, false);
        // The globe and its player position are one displayed frame. Between texture updates,
        // keep the following marker at its exact center instead of projecting newer coordinates.
        double[] player = globe ? new double[]{size * .5, size * .5}
                : project(data, client.player.getX(), client.player.getZ(), x, z, size, scale, false, yaw, pitch);
        if (player != null) AtlasPlayerMarker.draw(g, left + (int) Math.round(player[0]), top + (int) Math.round(player[1]),
                AtlasPlayerMarker.direction(false, 0, 0, 0, 0, client.player.getYRot()));
        g.disableScissor();
        label(g, "N", left + size / 2, top - 11);
        label(g, net.minecraft.network.chat.Component.translatable("orbis.atlas.east").getString(), left + size + 8, top + size / 2 - 4);
        label(g, "S", left + size / 2, top + size + 4);
        label(g, "W", left - 9, top + size / 2 - 4);
        int[] label = shownBlock(data, client.player.getX(), client.player.getZ());
        label(g, "X: " + label[0], left + size / 2, top + size + 16);
        label(g, "Y: " + client.player.getBlockY(), left + size / 2, top + size + 26);
        label(g, "Z: " + label[1], left + size / 2, top + size + 36);
    }

    /**
     * The block coordinates the label shows. The client keeps walking past the seam in one continuous frame; the
     * label shows the world's own coordinates.
     */
    static int[] shownBlock(AtlasSnapshot data, double x, double z) {
        if (data.wrapped()) {
            x = AtlasMapMath.canonical(x, data.minX(), data.span());
            z = AtlasMapMath.canonical(z, data.minZ(), data.span());
        }
        return new int[] {(int) Math.floor(x), (int) Math.floor(z)};
    }

    static int fittedSize(int width, int height, int requested) {
        return Math.max(32, Math.min(requested, Math.min(width / 3, (height - 90) / 2)));
    }
    private static void label(GuiGraphicsExtractor g, String text, int center, int y) {
        var font = Minecraft.getInstance().font;
        g.text(font, text, center - font.width(text) / 2, y, 0xffffffff, true);
    }

    static double[] project(AtlasSnapshot data, double x, double z, double centerX, double centerZ,
            int size, double scale, boolean globe, double yaw, double pitch) {
        return AtlasPlayerMarker.project(data, new AtlasMapRaster.View(size, size, centerX, centerZ,
                globe ? 1 : scale, globe, yaw, pitch, false, false), x, z);
    }

    @Override public void close() { texture.close(); flatTexture.close(); }
}
