package dev.orbis.engine;

import java.util.random.RandomGenerator;

/**
 * One octave of improved gradient noise whose lattice repeats exactly once per world width on X and Z.
 * Y is not periodic. Callers pass block coordinates; the octave's frequency is rounded so that a whole
 * number of lattice cells fits into the width, which keeps the seam invisible at every size.
 */
public final class PeriodicNoise {
    private final int[] permutation = new int[256];
    private final double xo, yo, zo;
    private final int width, cells;
    private final double scale;

    /**
     * @param width     world width in blocks; the noise repeats after exactly this many blocks
     * @param frequency desired lattice cells per block; rounded to {@code cells / width}
     */
    public PeriodicNoise(RandomGenerator random, int width, double frequency) {
        if (width <= 0 || !(frequency > 0)) throw new IllegalArgumentException("Invalid periodic noise");
        this.width = width;
        cells = cells(width, frequency);
        scale = cells / (double) width;
        xo = random.nextDouble() * 256; yo = random.nextDouble() * 256; zo = random.nextDouble() * 256;
        for (int i = 0; i < 256; i++) permutation[i] = i;
        for (int i = 0; i < 256; i++) {
            int j = random.nextInt(256 - i);
            int swap = permutation[i]; permutation[i] = permutation[i + j]; permutation[i + j] = swap;
        }
    }

    /** Whole lattice cells per world width; at least one, so the lowest octaves still tile. */
    public static int cells(int width, double frequency) { return (int) Math.max(1, Math.round(width * frequency)); }

    public int cells() { return cells; }

    /** Noise in roughly [-1, 1]; identical for x and x + width (and z and z + width). */
    public double sample(double x, double y, double z) {
        double u = Math.floorMod((long) Math.floor(x), (long) width) + (x - Math.floor(x));
        double w = Math.floorMod((long) Math.floor(z), (long) width) + (z - Math.floor(z));
        u = u * scale + xo;
        w = w * scale + zo;
        double v = y * scale + yo;
        int iu = (int) Math.floor(u), iv = (int) Math.floor(v), iw = (int) Math.floor(w);
        double fu = u - iu, fv = v - iv, fw = w - iw;
        int x0 = Math.floorMod(iu, cells), x1 = x0 + 1 == cells ? 0 : x0 + 1;
        int z0 = Math.floorMod(iw, cells), z1 = z0 + 1 == cells ? 0 : z0 + 1;
        int y0 = iv & 255, y1 = (iv + 1) & 255;
        double su = fade(fu), sv = fade(fv), sw = fade(fw);
        return lerp(sw,
                lerp(sv, lerp(su, grad(hash(x0, y0, z0), fu, fv, fw), grad(hash(x1, y0, z0), fu - 1, fv, fw)),
                         lerp(su, grad(hash(x0, y1, z0), fu, fv - 1, fw), grad(hash(x1, y1, z0), fu - 1, fv - 1, fw))),
                lerp(sv, lerp(su, grad(hash(x0, y0, z1), fu, fv, fw - 1), grad(hash(x1, y0, z1), fu - 1, fv, fw - 1)),
                         lerp(su, grad(hash(x0, y1, z1), fu, fv - 1, fw - 1), grad(hash(x1, y1, z1), fu - 1, fv - 1, fw - 1))));
    }

    private int hash(int x, int y, int z) {
        // Lattice indices beyond 255 reuse the table, exactly as plain Perlin noise does every 256 cells.
        return permutation[(permutation[(permutation[x & 255] + y) & 255] + (z & 255)) & 255];
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static double lerp(double t, double a, double b) { return a + t * (b - a); }

    /** The twelve cube-edge gradients, padded to sixteen. */
    private static double grad(int hash, double x, double y, double z) {
        return switch (hash & 15) {
            case 0, 12 -> x + y;
            case 1, 14 -> -x + y;
            case 2 -> x - y;
            case 3 -> -x - y;
            case 4 -> x + z;
            case 5 -> -x + z;
            case 6 -> x - z;
            case 7 -> -x - z;
            case 8 -> y + z;
            case 9, 13 -> -y + z;
            case 10 -> y - z;
            default -> -y - z;
        };
    }
}
