package dev.orbis.atlas;

/** Coarse fictional geography shared by every world. Never reads a seed, chunk or biome. */
final class AtlasUnknownTerrain {
    private static final int SIZE = 64;
    private static final double BLOCKS_PER_TEXEL = 64;
    static final int WATER = 0xff5c5c5c;
    static final int LAND = 0xffababab;
    private static final int[] PALETTE = {WATER, LAND};
    private static final int[][] LEVELS = build();

    static int level(double blocksPerPixel) {
        return Math.clamp(Math.getExponent(Math.max(1, blocksPerPixel / BLOCKS_PER_TEXEL)), 0, 6);
    }

    static int color(double x, double z, int level) {
        int size = SIZE >> level;
        double step = BLOCKS_PER_TEXEL * (1 << level);
        int tx = (int) Math.floor(x / step) & (size - 1);
        int tz = (int) Math.floor(z / step) & (size - 1);
        return LEVELS[level][tz * size + tx];
    }

    private static int[][] build() {
        int[][] levels = new int[7][];
        levels[0] = new int[SIZE * SIZE];
        for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {
            double u = x / (double) SIZE, v = z / (double) SIZE;
            double coast = .72 * noise(u * 4, v * 4, 4) + .22 * noise(u * 8, v * 8, 8)
                    + .06 * noise(u * 16, v * 16, 16);
            // Two flat gray inks distinguish fictional water and land only.
            // Deliberately no hill shading, fine grain, height contours or real biome data.
            levels[0][z * SIZE + x] = coast < .48 ? WATER : LAND;
        }
        for (int level = 1; level < levels.length; level++) {
            int size = SIZE >> level, previousSize = size * 2;
            levels[level] = new int[size * size];
            for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
                int i = z * 2 * previousSize + x * 2;
                int[] previous = levels[level - 1];
                int winner = WATER, most = -1;
                for (int color : PALETTE) {
                    int count = (previous[i] == color ? 1 : 0) + (previous[i + 1] == color ? 1 : 0)
                            + (previous[i + previousSize] == color ? 1 : 0)
                            + (previous[i + previousSize + 1] == color ? 1 : 0);
                    if (count > most) { most = count; winner = color; }
                }
                levels[level][z * size + x] = winner;
            }
        }
        return levels;
    }

    private static double noise(double x, double z, int period) {
        int ix = (int) x, iz = (int) z;
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, iz, period), b = hash(ix + 1, iz, period);
        double c = hash(ix, iz + 1, period), d = hash(ix + 1, iz + 1, period);
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }

    private static double hash(int x, int z, int period) {
        int value = (x & (period - 1)) * 0x1f1f1f1f ^ (z & (period - 1)) * 0x45d9f3b ^ period;
        value = (value ^ value >>> 16) * 0x45d9f3b;
        value = (value ^ value >>> 16) * 0x45d9f3b;
        return ((value ^ value >>> 16) & 0xffff) / 65535.0;
    }

    private AtlasUnknownTerrain() {}
}
