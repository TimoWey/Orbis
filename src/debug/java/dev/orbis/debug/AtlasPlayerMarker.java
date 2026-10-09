package dev.orbis.debug;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A north-up heading projected through the same chart as the terrain. */
final class AtlasPlayerMarker {
    private static final int RADIUS = 6;
    private static final int HEADINGS = 16;
    private static final int[][] RUNS = buildRuns();
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
        return forward >= -3 && forward <= 5 && across <= (5 - forward) * .5;
    }

    /** Small, two-color pixel triangle. No bevel, gradient or subpixel rotation. */
    static int colorAt(int x, int y, double dx, double dy) {
        if (!filled(x, y, dx, dy)) {
            if (filled(x - 1, y, dx, dy) || filled(x + 1, y, dx, dy)
                    || filled(x, y - 1, dx, dy) || filled(x, y + 1, dx, dy)) return 0xff17191b;
            return 0;
        }
        return 0xffdedbd2;
    }

    /** Use the view actually displayed by the texture, including on throttled frames. */
    static double[] project(DebugSnapshot data, DebugMapRaster.View view, double x, double z) {
        if (view.globe()) {
            double[] point = DebugMapMath.globe((x - data.minX()) / data.span(),
                    (z - data.minZ()) / data.span(), view.yaw(), view.pitch());
            if (point[2] <= 0) return null;
            double radius = Math.max(8, Math.min(view.width(), view.height()) * .5 - 4) * view.scale();
            return new double[]{view.width() * .5 + point[0] * radius, view.height() * .5 - point[1] * radius};
        }
        if (data.wrapped()) {
            x = DebugMapMath.canonical(x, data.minX(), data.span());
            z = DebugMapMath.canonical(z, data.minZ(), data.span());
        }
        return new double[]{view.width() * .5 + (x - view.centerX()) * view.scale(),
                view.height() * .5 + (z - view.centerZ()) * view.scale()};
    }

    private static int[][] buildRuns() {
        int[][] frames = new int[HEADINGS][];
        for (int heading = 0; heading < HEADINGS; heading++) {
            double angle = heading * Math.PI * 2 / HEADINGS;
            double dx = Math.cos(angle), dy = Math.sin(angle);
            var runs = new java.util.ArrayList<Integer>();
            for (int row = -RADIUS; row <= RADIUS; row++) {
                int start = -RADIUS, previous = 0;
                for (int col = -RADIUS; col <= RADIUS + 1; col++) {
                    int color = col <= RADIUS ? colorAt(col, row, dx, dy) : 0;
                    if (color != previous) {
                        if (previous != 0) {
                            runs.add(start); runs.add(row); runs.add(col); runs.add(previous);
                        }
                        start = col; previous = color;
                    }
                }
            }
            frames[heading] = runs.stream().mapToInt(Integer::intValue).toArray();
        }
        return frames;
    }

    static void draw(GuiGraphicsExtractor g, int x, int y, double[] direction) {
        int heading = (int) Math.round(Math.atan2(direction[1], direction[0]) * HEADINGS / (2 * Math.PI));
        int[] runs = RUNS[Math.floorMod(heading, HEADINGS)];
        for (int i = 0; i < runs.length; i += 4)
            g.fill(x + runs[i], y + runs[i + 1], x + runs[i + 2], y + runs[i + 1] + 1, runs[i + 3]);
    }
    private AtlasPlayerMarker() {}
}
