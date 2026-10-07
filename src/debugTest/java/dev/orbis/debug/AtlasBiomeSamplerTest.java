package dev.orbis.debug;

import com.toroidalworld.api.v1.option.WorldOptions;
import com.toroidalworld.engine.gen.LoopedChunkGenerator;
import com.toroidalworld.engine.noise.GenerationTransformerContext;
import dev.orbis.OrbisShape;
import dev.orbis.WorldSize;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasBiomeSamplerTest {
    static HolderLookup.Provider registries;
    @BeforeAll static void setup() {
        if (!WorldOptions.all().contains(OrbisShape.ENABLED)) WorldOptions.register(OrbisShape.ENABLED);
        registries = VanillaRegistries.createWorldLookup();
    }

    @Test void exhaustedBudgetAndCancellationNeverStartAnotherQueryAndResumeExactly() {
        var plains = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var clock = new AtomicInteger();
        var queries = new AtomicInteger();
        var sampler = new AtlasBiomeSampler((x, y, z) -> { queries.incrementAndGet(); return plains; },
                -1024, -1024, 2048, new int[] {63, -48, 32, 128}, clock::getAndIncrement);
        var received = new ArrayList<Integer>();
        AtlasBiomeSampler.Sink sink = (index, x, y, z, biome, color) -> received.add(index);
        assertEquals(0, sampler.run(0, 0, () -> true, sink));
        assertEquals(0, queries.get());
        assertEquals(3, sampler.run(0, 4, () -> true, sink));
        assertEquals(List.of(0, 1, 2), received);
        assertEquals(5, sampler.run(3, Long.MAX_VALUE, () -> queries.get() < 5, sink));
        assertEquals(List.of(0, 1, 2, 3, 4), received);
        assertEquals(5, queries.get());
    }

    @Test void fastSourcesUseTheAvailableBudgetWithoutTheOld256SampleLimit() {
        var plains = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var clock = new AtomicInteger();
        var sampler = new AtlasBiomeSampler((x, y, z) -> plains,
                -1024, -1024, 2048, new int[] {32, 16, 32, 128}, clock::getAndIncrement);
        var indices = new ArrayList<Integer>();
        assertEquals(3000, sampler.run(0, 3000, () -> true,
                (i, x, y, z, biome, color) -> indices.add(i)));
        assertEquals(3000, indices.size());
        assertEquals(2999, indices.getLast());
    }

    @Test void callerHeightsAndNegativeCoordinatesArePreservedIncludingDuplicateLayers() {
        var plains = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var quart = new ArrayList<List<Integer>>();
        int[] heights = {32, 16, 32, 127};
        var sampler = new AtlasBiomeSampler((x, y, z) -> { quart.add(List.of(x, y, z)); return plains; },
                -1024, -2048, 2048, heights, () -> 0);
        heights[0] = 99;
        var samples = new ArrayList<List<Integer>>();
        int start = DebugSurvey.TOTAL - 5;
        assertEquals(DebugSurvey.TOTAL, sampler.run(start, 1, () -> true,
                (i, x, y, z, biome, color) -> samples.add(List.of(i, x, y, z))));
        assertEquals(List.of(start, 1012, 127, -4), samples.getFirst());
        assertEquals(List.of(DebugSurvey.TOTAL - 4, 1020, 32, -4), samples.get(1));
        assertEquals(List.of(255, 8, -1), quart.get(1));
        assertEquals(quart.get(1), quart.get(3));
        assertEquals(5, quart.size(), "Duplicate layers still contribute separate hits");
    }

    @Test void cachedSamplerExactlyMatchesAllUncachedTinySamplesInEveryDimension() {
        var dimensions = OrbisShape.apply(WorldPresets.createNormalWorldDimensions(registries), WorldSize.Preset.TINY.size);
        for (var dimension : List.of(LevelStem.OVERWORLD, LevelStem.NETHER, LevelStem.END)) {
            var generator = (LoopedChunkGenerator) dimensions.get(dimension).orElseThrow().generator();
            var random = GenerationTransformerContext.withRouterBuild(generator.transformer(),
                    () -> RandomState.create(registries.lookupOrThrow(Registries.NOISE), 8172231L, generator.generatorSettings().value()));
            int span = dimension.equals(LevelStem.NETHER) ? WorldSize.Preset.TINY.size.netherBlocks()
                    : WorldSize.Preset.TINY.size.overworldBlocks();
            int[] heights = dimension.equals(LevelStem.NETHER) ? new int[] {32, 16, 32, 128}
                    : dimension.equals(LevelStem.END) ? new int[] {0, 16, 32, 128} : new int[] {63, -48, 32, 128};
            BiomeResolver expected = generator.getBiomeSource().createUncachedResolver(random);
            var actual = AtlasBiomeSampler.create(generator.getBiomeSource(), random, -span / 2, -span / 2, span, heights);
            // No ServerLevel, chunk source, or chunk exists in this test. Both paths use
            // the actual wrapped generator's seeded climate field and all four layers.
            assertEquals(DebugSurvey.TOTAL, actual.run(0, Long.MAX_VALUE, () -> true, (i, x, y, z, biome, color) -> {
                int cell = i / DebugSurvey.LAYERS;
                assertEquals(DebugMapMath.sampleCoordinate(-span / 2, span, cell % DebugSurvey.GRID, DebugSurvey.GRID), x);
                assertEquals(DebugMapMath.sampleCoordinate(-span / 2, span, cell / DebugSurvey.GRID, DebugSurvey.GRID), z);
                assertEquals(heights[i % DebugSurvey.LAYERS], y);
                String id = expected.getNoiseBiome(x >> 2, y >> 2, z >> 2).unwrapKey().orElseThrow().identifier().toString();
                assertEquals(id, biome, dimension.identifier() + " sample " + i);
                assertEquals(DebugMapMath.biomeColor(id), color);
            }));
        }
    }
}
