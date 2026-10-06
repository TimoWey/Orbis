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
        int size = Math.clamp(Math.min(g.guiWidth() / 5, g.guiHeight() / 3), 64, 128);
        int corner = AtlasPreferences.corner();
        int left = corner % 2 == 0 ? g.guiWidth() - size - 8 : 8;
        int top = corner < 2 ? 8 : g.guiHeight() - size - 30;
        boolean globe = AtlasPreferences.globe() && data.wrapped();
        // Quantized camera movement avoids rebuilding a terrain texture for subpixel motion.
        double x = Math.floor(client.player.getX() / 4) * 4;
        double z = Math.floor(client.player.getZ() / 4) * 4;
        double yaw = -((x - data.minX()) / data.span() - .5) * Math.PI * 2;
        double pitch = (.5 - (z - data.minZ()) / data.span()) * Math.PI;
        double scale = size / 256.0;
        g.fill(left - 2, top - 2, left + size + 2, top + size + 23, 0xb8101923);
        texture.draw(g, data, new DebugMapRaster.View(size, size, x, z, globe ? 1 : scale,
                globe, yaw, pitch, false, false), left, top);
        g.enableScissor(left, top, left + size, top + size);
        for (var point : AtlasWaypoints.SHARED.points()) {
            double[] p = project(data, point.x(), point.z(), x, z, size, scale, globe, yaw, pitch);
            if (p == null) continue;
            int px = left + (int) p[0], py = top + (int) p[1];
            g.outline(px - 2, py - 2, 5, 5, 0xffffd279);
        }
        double[] player = project(data, client.player.getX(), client.player.getZ(), x, z, size, scale, globe, yaw, pitch);
        if (player != null) {
            int px = left + (int) player[0], py = top + (int) player[1];
            g.fill(px - 2, py - 2, px + 3, py + 3, 0xff101820);
            g.fill(px - 1, py - 1, px + 2, py + 2, 0xffffffff);
            // Direction marker uses the same projection as the terrain, including globe rotation.
            double angle = Math.toRadians(client.player.getYRot());
            double ahead = globe ? data.span() / 80.0 : 12;
            double[] facing = project(data, client.player.getX() - Math.sin(angle) * ahead,
                    client.player.getZ() + Math.cos(angle) * ahead, x, z, size, scale, globe, yaw, pitch);
            if (facing != null) g.fill(left + (int) facing[0] - 1, top + (int) facing[1] - 1,
                    left + (int) facing[0] + 2, top + (int) facing[1] + 2, 0xff70dfbd);
        }
        g.text(client.font, "N", left + size / 2 - 2, top + 3, 0xffedf3fa, true);
        g.disableScissor();
        String coordinates = client.player.getBlockX() + "  " + client.player.getBlockZ();
        g.text(client.font, client.font.plainSubstrByWidth(coordinates, size), left, top + size + 3, 0xffedf3fa, false);
        g.text(client.font, OrbisDebugClient.mapKey.getTranslatedKeyMessage().getString() + " · " + (globe ? "Globe" : "Map"),
                left, top + size + 13, 0xffafbecf, false);
    }

    static double[] project(DebugSnapshot data, double x, double z, double centerX, double centerZ,
            int size, double scale, boolean globe, double yaw, double pitch) {
        if (globe) {
            double[] point = DebugMapMath.globe((x - data.minX()) / data.span(), (z - data.minZ()) / data.span(), yaw, pitch);
            if (point[2] <= 0) return null;
            double radius = Math.max(8, size * .5 - 4);
            return new double[] {size * .5 + point[0] * radius, size * .5 - point[1] * radius};
        }
        double dx = x - centerX, dz = z - centerZ;
        if (data.wrapped()) {
            dx -= Math.floor(dx / data.span() + .5) * data.span();
            dz -= Math.floor(dz / data.span() + .5) * data.span();
        }
        return new double[] {size * .5 + dx * scale, size * .5 + dz * scale};
    }

    @Override public void close() { texture.close(); }
}
