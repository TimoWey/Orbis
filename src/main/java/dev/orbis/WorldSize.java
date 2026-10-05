package dev.orbis;

/** Immutable square bounds. Widths are multiples of 16 chunks so both centred
 * edges map exactly to chunk edges in the Nether at 1:8. */
public record WorldSize(int overworldChunks) {
    // Keep old alpha saves readable; new selections start at Tiny.
    public static final int LEGACY_MIN_CHUNKS = 128;
    public static final int MIN_CHUNKS = 1_024;
    public static final int STEP_CHUNKS = 16;
    public static final int EARTH_CHUNKS = 1_448_160;
    public static final WorldSize DEFAULT = new WorldSize(76_544);

    public WorldSize {
        if (overworldChunks < LEGACY_MIN_CHUNKS || overworldChunks > EARTH_CHUNKS
                || overworldChunks % STEP_CHUNKS != 0) {
            throw new IllegalArgumentException("Stored width must be a multiple of 16 between 128 and " + EARTH_CHUNKS + " chunks.");
        }
    }

    /** Policy for new choices, separate from legacy save decoding. */
    public static WorldSize select(int chunks) {
        if (chunks < MIN_CHUNKS) throw new IllegalArgumentException("Choose Tiny or larger.");
        return new WorldSize(chunks);
    }

    /** Logarithmic travel preserves useful precision near Tiny. */
    public static WorldSize fromSlider(double position) {
        if (!Double.isFinite(position)) throw new IllegalArgumentException("Slider position must be finite.");
        double clamped = Math.clamp(position, 0.0, 1.0);
        double chunks = MIN_CHUNKS * Math.pow((double) EARTH_CHUNKS / MIN_CHUNKS, clamped);
        int snapped = (int) Math.round(chunks / STEP_CHUNKS) * STEP_CHUNKS;
        return select(Math.clamp(snapped, MIN_CHUNKS, EARTH_CHUNKS));
    }

    public double sliderPosition() {
        return Math.clamp(Math.log((double) overworldChunks / MIN_CHUNKS)
                / Math.log((double) EARTH_CHUNKS / MIN_CHUNKS), 0.0, 1.0);
    }

    public int netherChunks() { return overworldChunks / 8; }
    public int overworldBlocks() { return Math.multiplyExact(overworldChunks, 16); }
    public int netherBlocks() { return overworldBlocks() / 8; }
    public long overworldChunkCount() { return (long) overworldChunks * overworldChunks; }
    public long netherChunkCount() { return (long) netherChunks() * netherChunks(); }
    public long areaSquareMetres() { return overworldChunkCount() * 256L; }
    public double areaSquareKilometres() { return areaSquareMetres() / 1_000_000.0; }

    public String label() {
        for (Preset preset : Preset.values()) if (equals(preset.size)) return preset.label;
        return "Custom";
    }

    public enum Preset {
        TINY("Tiny", MIN_CHUNKS), SMALL("Small", 8_832),
        NORMAL("Normal", 76_544), LARGE("Large", 625_008),
        EARTH("Earth", EARTH_CHUNKS);
        public final String label;
        public final WorldSize size;
        Preset(String label, int chunks) { this.label = label; this.size = new WorldSize(chunks); }
    }
}
