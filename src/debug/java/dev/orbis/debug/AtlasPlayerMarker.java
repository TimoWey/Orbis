package dev.orbis.debug;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A north-up heading projected through the same chart as the terrain. */
final class AtlasPlayerMarker {
    static double[] direction(boolean globe, double u, double v, double yaw, double pitch, double degrees) {
        double angle = Math.toRadians(degrees);
        double dx = -Math.sin(angle), dy = Math.cos(angle);
        if (globe) {
            double[] from = DebugMapMath.globe(u, v, yaw, pitch);
            double[] to = DebugMapMath.globe(u + dx * 1e-5, v + dy * 1e-5, yaw, pitch);
            dx = to[0] - from[0]; dy = from[1] - to[1];
        }
        double length = Math.hypot(dx, dy);
        return length < 1e-12 ? new double[]{0, -1} : new double[]{dx / length, dy / length};
    }

    static boolean filled(double x, double y, double dx, double dy) {
        double forward = x * dx + y * dy, across = Math.abs(x * dy - y * dx);
        return forward >= -4 && forward <= 7 && across <= (7 - forward) * .45
                && (forward >= -1 || across >= (-1 - forward) * .65);
    }

    static void draw(GuiGraphicsExtractor g, int x, int y, double[] direction) {
        double dx = direction[0], dy = direction[1];
        for (int row = -9; row <= 9; row++) {
            int start = -9, previous = 0;
            for (int col = -9; col <= 10; col++) {
                int color = 0;
                if (col <= 9) {
                    if (filled(col, row, dx, dy)) color = 0xffffffff;
                    else if (filled(col - 1, row, dx, dy) || filled(col + 1, row, dx, dy)
                            || filled(col, row - 1, dx, dy) || filled(col, row + 1, dx, dy)) color = 0xff101820;
                }
                if (color != previous) {
                    if (previous != 0) g.fill(x + start, y + row, x + col, y + row + 1, previous);
                    start = col; previous = color;
                }
            }
        }
    }
    private AtlasPlayerMarker() {}
}
