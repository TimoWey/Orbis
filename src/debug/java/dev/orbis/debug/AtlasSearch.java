package dev.orbis.debug;

import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Bounded existence search and independent map preview; neither owns chunks or world state. */
final class AtlasSearch {
    static final int QUICK_GRID = 16, QUICK_MAX = 1024, EXTRA = 4096, MAX = QUICK_MAX + 8 * EXTRA;
    static final int MAP_COARSE = QUICK_GRID * QUICK_GRID, MAP_MAX = MAP_COARSE + 256 * 256;
    record Saved(int cursor, int limit, boolean running, int mapCursor, int mapLimit, boolean mapping) {
        Saved {
            if (cursor < 0 || cursor > limit || limit < 1 || limit > MAX || mapCursor < 0
                    || mapCursor > mapLimit || (mapLimit != MAP_COARSE && mapLimit != MAP_MAX))
                throw new IllegalArgumentException("Invalid search checkpoint");
        }
    }
    record View(Saved progress, boolean allFound) {
        String label() {
            return allFound ? "All found" : progress.running ? "Searching" : progress.cursor < progress.limit
                    ? "Paused" : progress.limit == MAX ? "Limit reached" : "Pass complete";
        }
    }
    @FunctionalInterface interface Query { AtlasBiomeSampler.Style at(int x, int y, int z); }
    @FunctionalInterface interface Evidence { void accept(int x, int y, int z, String biome); }
    @FunctionalInterface interface Paint { void accept(int x, int z, int grid, int color); }
    private final int minX, minZ, span, minY, maxY;
    private final int[] heights;
    private final LongSupplier clock;
    private int cursor, limit, mapCursor, mapLimit = MAP_COARSE;
    private boolean running, mapping;

    AtlasSearch(int minX, int minZ, int span, int minY, int maxY, int[] heights, boolean enabled) {
        this(minX, minZ, span, minY, maxY, heights, enabled, System::nanoTime);
    }
    AtlasSearch(int minX, int minZ, int span, int minY, int maxY, int[] heights, boolean enabled, LongSupplier clock) {
        this.minX = minX; this.minZ = minZ; this.span = span; this.minY = minY; this.maxY = maxY; this.clock = clock;
        this.heights = Arrays.stream(heights).map(y -> Math.clamp(y, minY, maxY)).distinct().toArray();
        if (this.heights.length < 1 || this.heights.length > 4) throw new IllegalArgumentException("Expected 1-4 heights");
        limit = MAP_COARSE * this.heights.length;
        running = mapping = enabled;
    }
    Saved save() { return new Saved(cursor, limit, running, mapCursor, mapLimit, mapping); }
    void restore(Saved p) {
        cursor = p.cursor; limit = p.limit; running = p.running && cursor < limit;
        mapCursor = p.mapCursor; mapLimit = p.mapLimit; mapping = p.mapping && mapCursor < mapLimit;
    }
    void pause() { running = false; }
    void pauseMap() { mapping = false; }
    void toggle(boolean allFound) {
        if (running) { running = false; return; }
        if (allFound) return;
        if (cursor == limit) limit = Math.min(MAX, limit + EXTRA);
        running = cursor < limit;
    }
    void resample() { cursor = 0; limit = MAP_COARSE * heights.length; running = true; }
    void toggleMap() {
        if (mapping) { mapping = false; return; }
        if (mapCursor == mapLimit) mapLimit = MAP_MAX;
        mapping = mapCursor < mapLimit;
    }
    void search(long deadline, BooleanSupplier active, BooleanSupplier allFound, Query query, Evidence evidence) {
        while (running && active.getAsBoolean()) {
            if (allFound.getAsBoolean() || cursor >= limit) { running = false; break; }
            if (clock.getAsLong() >= deadline) break;
            int x, z, y;
            int quick = MAP_COARSE * heights.length;
            if (cursor < quick) {
                int cell = cursor / heights.length;
                x = DebugMapMath.sampleCoordinate(minX, span, cell % QUICK_GRID, QUICK_GRID);
                z = DebugMapMath.sampleCoordinate(minZ, span, cell / QUICK_GRID, QUICK_GRID);
                y = heights[cursor % heights.length];
            } else {
                // Shared distributed search for the remaining types, including extra vertical slices.
                // Absolute sequence indices make continuation independent of tick timing and restarts.
                int index = cursor - quick + 1;
                x = minX + (int) (fraction(index, 2) * span);
                z = minZ + (int) (fraction(index, 3) * span);
                int layer = (index - 1) % (heights.length + 1);
                // Advance the height sequence only for variable-height queries. Using index
                // itself locks the four-height case to multiples of base 5 (the bottom 20%).
                y = layer < heights.length ? heights[layer]
                        : minY + (int) (fraction(index / (heights.length + 1), 5) * (maxY - minY + 1));
            }
            var result = query.at(x, y, z);
            evidence.accept(x, y, z, result.id());
            cursor++;
        }
    }
    void map(long deadline, BooleanSupplier active, Query query, Evidence evidence, Paint paint) {
        while (mapping && active.getAsBoolean() && clock.getAsLong() < deadline) {
            if (mapCursor >= mapLimit) { mapping = false; break; }
            int grid = mapCursor < MAP_COARSE ? QUICK_GRID : DebugSurvey.GRID;
            int cell = mapCursor < MAP_COARSE ? mapCursor : mapCursor - MAP_COARSE;
            int x = DebugMapMath.sampleCoordinate(minX, span, cell % grid, grid);
            int z = DebugMapMath.sampleCoordinate(minZ, span, cell / grid, grid);
            var result = query.at(x, heights[0], z);
            evidence.accept(x, heights[0], z, result.id());
            paint.accept(cell % grid, cell / grid, grid, result.color());
            mapCursor++;
            if (mapCursor == mapLimit) mapping = false;
        }
    }
    private static double fraction(int index, int base) {
        double value = 0, weight = 1.0 / base;
        while (index > 0) { value += (index % base) * weight; index /= base; weight /= base; }
        return value;
    }
}
