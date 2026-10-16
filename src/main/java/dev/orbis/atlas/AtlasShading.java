package dev.orbis.atlas;

/**
 * Vanilla map relief for one 16x16 tile: land by the height step to the north column, water by depth,
 * both with vanilla's checkerboard dither. Pure functions; every sampler shades the same way.
 */
final class AtlasShading {
    static final int LOW = 0, NORMAL = 1, HIGH = 2;
    /** Columns with this height (fallback, empty slice, Nether) keep their colour and give no relief. */
    static final int FLAT = Integer.MIN_VALUE;
    /** Vanilla picks LOW for every depth from here on, so deeper scans cannot change a pixel. */
    static final int WATER_DEPTH_LIMIT = 10;
    // Vanilla's 180:220:255 ratios softened and normalised so flat ground keeps its raw MapColor,
    // matching tiles archived before shading existed.
    private static final int[] MODIFIERS = {216, 255, 282};

    static int landBrightness(int height, int northHeight, int x, int z) {
        double step = height - northHeight + (((x + z) & 1) - .5) * .4;
        return step > .6 ? HIGH : step < -.6 ? LOW : NORMAL;
    }

    static int waterBrightness(int depth, int x, int z) {
        double shade = depth * .1 + ((x + z) & 1) * .2;
        return shade < .5 ? HIGH : shade > .9 ? LOW : NORMAL;
    }

    static int shade(int argb, int brightness) {
        int modifier = MODIFIERS[brightness];
        if (modifier == 255) return argb;
        int r = Math.min(255, (argb >> 16 & 255) * modifier / 255);
        int g = Math.min(255, (argb >> 8 & 255) * modifier / 255);
        int b = Math.min(255, (argb & 255) * modifier / 255);
        return argb & 0xff000000 | r << 16 | g << 8 | b;
    }

    /**
     * Shades {@code colors} in place. {@code depths[i] > 0} marks water. Row 0 has no in-tile north
     * neighbour, so it reuses the step to row 1; tiles never depend on chunks sampled at another time.
     */
    static void shadeTile(int[] colors, int[] heights, int[] depths, int originX, int originZ) {
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int i = z * 16 + x, height = heights[i];
            if (height == FLAT) continue;
            int worldX = originX + x, worldZ = originZ + z;
            if (depths[i] > 0) { colors[i] = shade(colors[i], waterBrightness(depths[i], worldX, worldZ)); continue; }
            int neighbour = z > 0 ? heights[i - 16] : heights[i + 16];
            int step = neighbour == FLAT ? 0 : z > 0 ? height - neighbour : neighbour - height;
            colors[i] = shade(colors[i], landBrightness(step, 0, worldX, worldZ));
        }
    }

    private AtlasShading() {}
}
