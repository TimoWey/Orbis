package dev.orbis.atlas;

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
        if (heights.length != DeveloperSurvey.LAYERS) throw new IllegalArgumentException("Expected four heights");
    }
    int run(int cursor, long deadline, BooleanSupplier active, Sink sink) {
        while (cursor < DeveloperSurvey.TOTAL && active.getAsBoolean() && System.nanoTime() < deadline) {
            int cell = cursor / DeveloperSurvey.LAYERS;
            int x = AtlasMapMath.sampleCoordinate(minX, span, cell % DeveloperSurvey.GRID, DeveloperSurvey.GRID);
            int z = AtlasMapMath.sampleCoordinate(minZ, span, cell / DeveloperSurvey.GRID, DeveloperSurvey.GRID);
            int y = heights[cursor % DeveloperSurvey.LAYERS];
            var style = sampler.query(x, y, z);
            sink.accept(cursor, x, y, z, style.id(), style.color());
            cursor++;
        }
        return cursor;
    }
}
