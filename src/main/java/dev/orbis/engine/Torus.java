package dev.orbis.engine;

/**
 * A square world that wraps on X and Z. Every block column exists once, inside
 * {@code [minBlock, minBlock + widthBlocks)}; any other coordinate is an image of one of them.
 */
public record Torus(int minBlock, int widthBlocks) {
    public static final int NETHER_SCALE = 8;

    public Torus {
        if (widthBlocks < 16 || (widthBlocks & 15) != 0 || (minBlock & 15) != 0)
            throw new IllegalArgumentException("Torus bounds must be whole chunks: " + minBlock + " + " + widthBlocks);
    }

    /** Centred on 0, as used by every Orbis preset. */
    public static Torus centered(int widthBlocks) { return new Torus(-widthBlocks / 2, widthBlocks); }

    public int widthChunks() { return widthBlocks >> 4; }
    public int minChunk() { return minBlock >> 4; }
    public boolean contains(int block) { return block >= minBlock && block - minBlock < widthBlocks; }
    public boolean containsChunk(int chunk) { return chunk >= minChunk() && chunk - minChunk() < widthChunks(); }

    public int fold(int block) { return minBlock + Math.floorMod(block - minBlock, widthBlocks); }
    public long fold(long block) { return minBlock + Math.floorMod(block - minBlock, (long) widthBlocks); }
    public int foldChunk(int chunk) { return minChunk() + Math.floorMod(chunk - minChunk(), widthChunks()); }
    public double fold(double block) {
        double offset = (block - minBlock) % widthBlocks;
        if (offset < 0) offset += widthBlocks;
        // A tiny negative remainder can round up to exactly the width.
        return offset >= widthBlocks ? minBlock : minBlock + offset;
    }

    /** Shortest signed distance from {@code from} to {@code to}, in [-width/2, width/2). */
    public double delta(double from, double to) {
        double d = (to - from) % widthBlocks;
        double half = widthBlocks / 2.0;
        if (d >= half) d -= widthBlocks;
        else if (d < -half) d += widthBlocks;
        return d;
    }
    public int deltaChunks(int from, int to) {
        int width = widthChunks(), d = Math.floorMod(to - from, width);
        return d >= (width + 1) / 2 ? d - width : d;
    }

    /** The image of {@code value} nearest to {@code reference}. */
    public double nearest(double value, double reference) { return reference + delta(reference, value); }
    public int nearestChunk(int chunk, int referenceChunk) { return referenceChunk + deltaChunks(referenceChunk, chunk); }

    /** The Nether is always exactly one eighth of the Overworld on each axis. */
    public Torus nether() {
        if (minBlock % (16 * NETHER_SCALE) != 0 || widthBlocks % (16 * NETHER_SCALE) != 0)
            throw new IllegalStateException("Overworld bounds are not divisible into whole Nether chunks");
        return new Torus(minBlock / NETHER_SCALE, widthBlocks / NETHER_SCALE);
    }
}
