package dev.orbis.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Compact north-up terrain map, or a globe oriented toward the live player. */
final class OrbisMinimap implements AutoCloseable {
    private final DebugMapTexture texture = new DebugMapTexture();

    void draw(GuiGraphicsExtractor g) {
        Minecraft client = Minecraft.getInstance();
        DebugSnapshot data = DebugSurvey.snapshot;
        if (client.player == null || client.level == null || client.gui.screen() != null || data == null
                || !data.dimension().equals(client.level.dimension().identifier().toString())) return;
        int size = fittedSize(g.guiWidth(), g.guiHeight(), AtlasPreferences.size());
        int corner = AtlasPreferences.corner();
        int left = corner % 2 == 0 ? g.guiWidth() - size - 16 : 16;
        int top = corner < 2 ? 16 : g.guiHeight() - size - 78;
        boolean globe = AtlasPreferences.globe(data.dimension()) && data.wrapped();
        // Quantized camera movement avoids rebuilding a terrain texture for subpixel motion.
        double x = Math.floor(client.player.getX() / 4) * 4;
        double z = Math.floor(client.player.getZ() / 4) * 4;
        double yaw = -((x - data.minX()) / data.span() - .5) * Math.PI * 2;
        double pitch = (.5 - (z - data.minZ()) / data.span()) * Math.PI;
        double scale = size / (double) Math.min(256, data.span());
        if (!globe && data.wrapped()) {
            x = DebugMapMath.clampCenter(DebugMapMath.canonical(x, data.minX(), data.span()), data.minX(), data.span(), size / scale);
            z = DebugMapMath.clampCenter(DebugMapMath.canonical(z, data.minZ(), data.span()), data.minZ(), data.span(), size / scale);
        }
        if (!globe) {
            g.fill(left - 3, top - 3, left + size + 3, top + size + 3, 0xff202020);
            g.outline(left - 2, top - 2, size + 4, size + 4, 0xffaaaaaa);
        }
        texture.draw(g, data, new DebugMapRaster.View(size, size, x, z, globe ? 1 : scale,
                globe, yaw, pitch, false, false), left, top);
        g.enableScissor(left, top, left + size, top + size);
        double[] player = project(data, client.player.getX(), client.player.getZ(), x, z, size, scale, globe, yaw, pitch);
        if (player != null) AtlasPlayerMarker.draw(g, left + (int) player[0], top + (int) player[1],
                AtlasPlayerMarker.direction(globe, (client.player.getX() - data.minX()) / data.span(),
                        (client.player.getZ() - data.minZ()) / data.span(), yaw, pitch, client.player.getYRot()));
        g.disableScissor();
        label(g, "N", left + size / 2, top - 11);
        label(g, net.minecraft.network.chat.Component.translatable("orbis.atlas.east").getString(), left + size + 8, top + size / 2 - 4);
        label(g, "S", left + size / 2, top + size + 4);
        label(g, "W", left - 9, top + size / 2 - 4);
        label(g, "X: " + client.player.getBlockX(), left + size / 2, top + size + 16);
        label(g, "Y: " + client.player.getBlockY(), left + size / 2, top + size + 26);
        label(g, "Z: " + client.player.getBlockZ(), left + size / 2, top + size + 36);
    }

    static int fittedSize(int width, int height, int requested) {
        return Math.max(32, Math.min(requested, Math.min(width / 3, (height - 90) / 2)));
    }
    private static void label(GuiGraphicsExtractor g, String text, int center, int y) {
        var font = Minecraft.getInstance().font;
        g.text(font, text, center - font.width(text) / 2, y, 0xffffffff, true);
    }

    static double[] project(DebugSnapshot data, double x, double z, double centerX, double centerZ,
            int size, double scale, boolean globe, double yaw, double pitch) {
        if (globe) {
            double[] point = DebugMapMath.globe((x - data.minX()) / data.span(), (z - data.minZ()) / data.span(), yaw, pitch);
            if (point[2] <= 0) return null;
            double radius = Math.max(8, size * .5 - 4);
            return new double[] {size * .5 + point[0] * radius, size * .5 - point[1] * radius};
        }
        if (data.wrapped()) {
            x = DebugMapMath.canonical(x, data.minX(), data.span());
            z = DebugMapMath.canonical(z, data.minZ(), data.span());
        }
        double dx = x - centerX, dz = z - centerZ;
        return new double[] {size * .5 + dx * scale, size * .5 + dz * scale};
    }

    @Override public void close() { texture.close(); }
}
