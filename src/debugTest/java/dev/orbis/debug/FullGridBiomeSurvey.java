package dev.orbis.debug;

import java.util.function.BooleanSupplier;

/** Historical full-grid workload, retained only for resolver parity tests and microbenchmarks. */
final class FullGridBiomeSurvey {
    @FunctionalInterface interface Sink {
        void accept(int index, int x, int y, int z, String biome, int color);
    }
    private final AtlasBiomeSampler sampler;
    private final int minX, minZ, span;
    private final int[] heights;

    FullGridBiomeSurvey(AtlasBiomeSampler sampler, int minX, int minZ, int span, int[] heights) {
        this.sampler = sampler; this.minX = minX; this.minZ = minZ; this.span = span;
        this.heights = heights.clone();
        if (heights.length != DebugSurvey.LAYERS) throw new IllegalArgumentException("Expected four heights");
    }
    int run(int cursor, long deadline, BooleanSupplier active, Sink sink) {
        while (cursor < DebugSurvey.TOTAL && active.getAsBoolean() && System.nanoTime() < deadline) {
            int cell = cursor / DebugSurvey.LAYERS;
            int x = DebugMapMath.sampleCoordinate(minX, span, cell % DebugSurvey.GRID, DebugSurvey.GRID);
            int z = DebugMapMath.sampleCoordinate(minZ, span, cell / DebugSurvey.GRID, DebugSurvey.GRID);
            int y = heights[cursor % DebugSurvey.LAYERS];
            var style = sampler.query(x, y, z);
            sink.accept(cursor, x, y, z, style.id(), style.color());
            cursor++;
        }
        return cursor;
    }
}
