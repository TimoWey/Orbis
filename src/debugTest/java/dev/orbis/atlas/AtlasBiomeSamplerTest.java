package dev.orbis.atlas;

import dev.orbis.OrbisShape;
import dev.orbis.engine.Tori;
import dev.orbis.engine.TorusChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
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
        registries = VanillaRegistries.createWorldLookup();
    }

    @Test void queriesUseQuartCoordinatesAndReuseStableBiomeStyles() {
        var plains = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var quart = new ArrayList<List<Integer>>();
        var sampler = new AtlasBiomeSampler((x, y, z) -> { quart.add(List.of(x, y, z)); return plains; });
        var first = sampler.query(-1, -63, -5);
        assertSame(first, sampler.query(1020, 127, -4));
        assertEquals(List.of(List.of(-1, -16, -2), List.of(255, 31, -1)), quart);
        assertEquals("minecraft:plains", first.id());
        assertEquals(AtlasMapMath.biomeColor(first.id()), first.color());
    }
    @Test void cachedSamplerExactlyMatchesAllUncachedTinySamplesInEveryDimension() {
        var dimensions = OrbisShape.apply(WorldPresets.createNormalWorldDimensions(registries), WorldSize.Preset.TINY.size);
        for (var dimension : List.of(LevelStem.OVERWORLD, LevelStem.NETHER, LevelStem.END)) {
            var generator = (NoiseBasedChunkGenerator) dimensions.get(dimension).orElseThrow().generator();
            var random = randomState(registries, generator, 8172231L);
            int span = dimension.equals(LevelStem.NETHER) ? WorldSize.Preset.TINY.size.netherBlocks()
                    : WorldSize.Preset.TINY.size.overworldBlocks();
            int[] heights = dimension.equals(LevelStem.NETHER) ? new int[] {32, 16, 32, 128}
                    : dimension.equals(LevelStem.END) ? new int[] {0, 16, 32, 128} : new int[] {63, -48, 32, 128};
            BiomeResolver expected = generator.getBiomeSource().createUncachedResolver(random);
            var actual = new FullGridBiomeSurvey(AtlasBiomeSampler.create(generator.getBiomeSource(), random), -span / 2, -span / 2, span, heights);
            // No ServerLevel, chunk source, or chunk exists in this test. Both paths use
            // the actual wrapped generator's seeded climate field and all four layers.
            assertEquals(DeveloperSurvey.TOTAL, actual.run(0, Long.MAX_VALUE, () -> true, (i, x, y, z, biome, color) -> {
                int cell = i / DeveloperSurvey.LAYERS;
                assertEquals(AtlasMapMath.sampleCoordinate(-span / 2, span, cell % DeveloperSurvey.GRID, DeveloperSurvey.GRID), x);
                assertEquals(AtlasMapMath.sampleCoordinate(-span / 2, span, cell / DeveloperSurvey.GRID, DeveloperSurvey.GRID), z);
                assertEquals(heights[i % DeveloperSurvey.LAYERS], y);
                String id = expected.getNoiseBiome(x >> 2, y >> 2, z >> 2).unwrapKey().orElseThrow().identifier().toString();
                assertEquals(id, biome, dimension.identifier() + " sample " + i);
                assertEquals(AtlasMapMath.biomeColor(id), color);
            }));
        }
    }

    @Test void nativeQuickChecksShareFindsWithMapAndStopWithinSmallBudgetForTinyAndLarge() {
        for (var preset : List.of(WorldSize.Preset.TINY, WorldSize.Preset.LARGE)) {
            var dimensions = OrbisShape.apply(WorldPresets.createNormalWorldDimensions(registries), preset.size);
            for (var dimension : List.of(LevelStem.OVERWORLD, LevelStem.NETHER)) {
                var generator = (NoiseBasedChunkGenerator) dimensions.get(dimension).orElseThrow().generator();
                var random = randomState(registries, generator, 8172231L);
                boolean nether = dimension.equals(LevelStem.NETHER);
                int span = nether ? preset.size.netherBlocks() : preset.size.overworldBlocks();
                int[] heights = nether ? new int[]{32, 16, 32, 128} : new int[]{63, -48, 32, 128};
                var sampler = AtlasBiomeSampler.create(generator.getBiomeSource(), random);
                var search = new AtlasSearch(-span / 2, -span / 2, span, nether ? 0 : -64, nether ? 255 : 319, heights, true);
                Set<String> possible = new HashSet<>(), found = new HashSet<>();
                generator.getBiomeSource().possibleBiomes().forEach(b -> possible.add(b.unwrapKey().orElseThrow().identifier().toString()));
                var queries = new AtomicInteger();
                AtlasSearch.Query query = (x,y,z) -> { queries.incrementAndGet(); return sampler.query(x,y,z); };
                AtlasSearch.Evidence evidence = (x,y,z,id) -> { assertTrue(possible.contains(id)); found.add(id); };
                search.search(Long.MAX_VALUE, () -> true, () -> found.containsAll(possible), query, evidence);
                search.map(Long.MAX_VALUE, () -> true, query, evidence, (x,z,grid,color) -> {});
                assertTrue(queries.get() <= (nether ? 768 : 1024) + 256);
                assertFalse(search.save().running()); assertFalse(search.save().mapping());
                System.out.printf("ATLAS_QUICK preset=%s dimension=%s queries=%d found=%d/%d%n",
                        preset, dimension.identifier(), queries.get(), found.size(), possible.size());
                if (!found.containsAll(possible)) {
                    int before = queries.get(), known = found.size();
                    search.toggle(false);
                    search.search(Long.MAX_VALUE, () -> true, () -> found.containsAll(possible), query, evidence);
                    assertTrue(queries.get() - before <= AtlasSearch.EXTRA);
                    assertTrue(found.size() >= known);
                    System.out.printf("ATLAS_DEEPEN preset=%s dimension=%s extra_queries=%d found=%d/%d%n",
                            preset, dimension.identifier(), queries.get() - before, found.size(), possible.size());
                }
            }
        }
    }

    /** The seeded noise of a generator, periodic for an Orbis torus. */
    static RandomState randomState(HolderLookup.Provider registries, NoiseBasedChunkGenerator generator, long seed) {
        java.util.function.Supplier<RandomState> create = () -> RandomState.create(registries.lookupOrThrow(Registries.NOISE), seed,
                generator.generatorSettings().value());
        return generator instanceof TorusChunkGenerator torus ? Tori.creatingRandomState(torus.torus(), create) : create.get();
    }
}
