package dev.orbis.atlas;

import com.toroidalworld.engine.gen.LoopedChunkGenerator;
import com.toroidalworld.engine.noise.GenerationTransformerContext;
import dev.orbis.OrbisShape;
import dev.orbis.WorldSize;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Historical full-grid resolver microbenchmark (not live atlas scheduling); set ORBIS_ATLAS_BENCHMARK=1 and select this test class. */
@EnabledIfEnvironmentVariable(named = "ORBIS_ATLAS_BENCHMARK", matches = "1")
class AtlasBiomeSamplerBenchmark {
    private static final long BUDGET = 3_000_000L;
    private record Result(int ticks, long nanos, Map<String, Integer> hits,
            Map<String, AtlasSnapshot.Place> locations, Map<Integer, Integer> colors) {}

    @Test void compareIdenticalTinySurveysWithNativeWrappedResolvers() {
        AtlasBiomeSamplerTest.setup();
        var registries = AtlasBiomeSamplerTest.registries;
        var dimensions = OrbisShape.applyLegacy(WorldPresets.createNormalWorldDimensions(registries), WorldSize.Preset.TINY.size);
        for (var dimension : List.of(LevelStem.OVERWORLD, LevelStem.NETHER)) {
            var generator = (LoopedChunkGenerator) dimensions.get(dimension).orElseThrow().generator();
            var random = GenerationTransformerContext.withRouterBuild(generator.transformer(),
                    () -> RandomState.create(registries.lookupOrThrow(Registries.NOISE), 8172231L, generator.generatorSettings().value()));
            int span = dimension.equals(LevelStem.NETHER) ? WorldSize.Preset.TINY.size.netherBlocks()
                    : WorldSize.Preset.TINY.size.overworldBlocks();
            int[] heights = dimension.equals(LevelStem.NETHER) ? new int[] {32, 16, 32, 128} : new int[] {63, -48, 32, 128};
            // One whole untimed survey per implementation warms the real noise functions and JIT.
            baseline(generator.getBiomeSource().createUncachedResolver(random), span, heights);
            optimized(new FullGridBiomeSurvey(AtlasBiomeSampler.create(generator.getBiomeSource(), random), -span / 2, -span / 2, span, heights));
            for (int round = 1; round <= 3; round++) {
                Result old = baseline(generator.getBiomeSource().createUncachedResolver(random), span, heights);
                Result next = optimized(new FullGridBiomeSurvey(AtlasBiomeSampler.create(generator.getBiomeSource(), random), -span / 2, -span / 2, span, heights));
                assertEquals(old.hits, next.hits);
                assertEquals(old.locations, next.locations);
                assertEquals(old.colors, next.colors);
                System.out.printf(Locale.ROOT,
                        "ATLAS_BENCH dimension=%s round=%d samples=%d old_cpu_ms=%.1f new_cpu_ms=%.1f old_ticks=%d new_ticks=%d old_20tps_seconds=%.2f new_20tps_seconds=%.2f%n",
                        dimension.identifier(), round, DeveloperSurvey.TOTAL,
                        old.nanos / 1e6, next.nanos / 1e6, old.ticks, next.ticks, old.ticks / 20.0, next.ticks / 20.0);
            }
        }
    }

    private static Result baseline(BiomeResolver resolver, int span, int[] heights) {
        Map<String, Integer> hits = new HashMap<>();
        Map<String, AtlasSnapshot.Place> locations = new HashMap<>();
        Map<Integer, Integer> colors = new HashMap<>();
        int cursor = 0, ticks = 0;
        long start = System.nanoTime();
        while (cursor < DeveloperSurvey.TOTAL) {
            long deadline = System.nanoTime() + BUDGET;
            for (int count = 0; count < 256 && cursor < DeveloperSurvey.TOTAL && System.nanoTime() < deadline; count++) {
                int cell = cursor / DeveloperSurvey.LAYERS, layer = cursor % DeveloperSurvey.LAYERS;
                int x = AtlasMapMath.sampleCoordinate(-span / 2, span, cell % DeveloperSurvey.GRID, DeveloperSurvey.GRID);
                int z = AtlasMapMath.sampleCoordinate(-span / 2, span, cell / DeveloperSurvey.GRID, DeveloperSurvey.GRID);
                int y = heights[layer];
                String id = resolver.getNoiseBiome(x >> 2, y >> 2, z >> 2).unwrapKey()
                        .map(key -> key.identifier().toString()).orElse("unregistered");
                locations.putIfAbsent(id, new AtlasSnapshot.Place(id, x, y, z));
                hits.merge(id, 1, Integer::sum);
                if (layer == 0) colors.put(cell, AtlasMapMath.biomeColor(id));
                cursor++;
            }
            ticks++;
        }
        return new Result(ticks, System.nanoTime() - start, hits, locations, colors);
    }

    private static Result optimized(FullGridBiomeSurvey sampler) {
        Map<String, Integer> hits = new HashMap<>();
        Map<String, AtlasSnapshot.Place> locations = new HashMap<>();
        Map<Integer, Integer> colors = new HashMap<>();
        FullGridBiomeSurvey.Sink sink = (index, x, y, z, id, color) -> {
            if (!locations.containsKey(id)) locations.put(id, new AtlasSnapshot.Place(id, x, y, z));
            hits.merge(id, 1, Integer::sum);
            if (index % DeveloperSurvey.LAYERS == 0) colors.put(index / DeveloperSurvey.LAYERS, color);
        };
        int cursor = 0, ticks = 0;
        long start = System.nanoTime();
        while (cursor < DeveloperSurvey.TOTAL) {
            cursor = sampler.run(cursor, System.nanoTime() + BUDGET, () -> true, sink);
            ticks++;
        }
        return new Result(ticks, System.nanoTime() - start, hits, locations, colors);
    }
}
