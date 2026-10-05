package dev.orbis;

/** Immutable square bounds. Widths are multiples of 16 chunks so both centred
 * edges map exactly to chunk edges in the Nether at 1:8. */
public record WorldSize(int overworldChunks) {
    public static final int MIN_CHUNKS = 128;
    public static final int EARTH_CHUNKS = 1_411_456;
    public static final WorldSize DEFAULT = new WorldSize(1024);

    public WorldSize {
        if (overworldChunks < MIN_CHUNKS || overworldChunks > EARTH_CHUNKS
                || overworldChunks % 16 != 0) {
            throw new IllegalArgumentException("Width must be a multiple of 16 between 128 and 1411456 chunks.");
        }
    }

    public int netherChunks() { return overworldChunks / 8; }
    public int overworldBlocks() { return Math.multiplyExact(overworldChunks, 16); }
    public int netherBlocks() { return overworldBlocks() / 8; }
    public long overworldChunkCount() { return (long) overworldChunks * overworldChunks; }
    public long netherChunkCount() { return (long) netherChunks() * netherChunks(); }

    public enum Preset {
        VERY_SMALL("Very small", 128), SMALL("Small", 256),
        MEDIUM("Medium / Normal", 1024), LARGE("Large", 2048),
        VERY_LARGE("Very large", 4096), EARTH("Earth (experimental)", EARTH_CHUNKS);
        public final String label;
        public final WorldSize size;
        Preset(String label, int chunks) { this.label = label; this.size = new WorldSize(chunks); }
    }
}
