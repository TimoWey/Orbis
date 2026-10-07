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

    @Test void queriesUseQuartCoordinatesAndReuseStableBiomeStyles() {
        var plains = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var quart = new ArrayList<List<Integer>>();
        var sampler = new AtlasBiomeSampler((x, y, z) -> { quart.add(List.of(x, y, z)); return plains; });
        var first = sampler.query(-1, -63, -5);
        assertSame(first, sampler.query(1020, 127, -4));
        assertEquals(List.of(List.of(-1, -16, -2), List.of(255, 31, -1)), quart);
        assertEquals("minecraft:plains", first.id());
        assertEquals(DebugMapMath.biomeColor(first.id()), first.color());
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
            var actual = new FullGridBiomeSurvey(AtlasBiomeSampler.create(generator.getBiomeSource(), random), -span / 2, -span / 2, span, heights);
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

    @Test void nativeQuickChecksShareFindsWithMapAndStopWithinSmallBudgetForTinyAndLarge() {
        for (var preset : List.of(WorldSize.Preset.TINY, WorldSize.Preset.LARGE)) {
            var dimensions = OrbisShape.apply(WorldPresets.createNormalWorldDimensions(registries), preset.size);
            for (var dimension : List.of(LevelStem.OVERWORLD, LevelStem.NETHER)) {
                var generator = (LoopedChunkGenerator) dimensions.get(dimension).orElseThrow().generator();
                var random = GenerationTransformerContext.withRouterBuild(generator.transformer(),
                        () -> RandomState.create(registries.lookupOrThrow(Registries.NOISE), 8172231L, generator.generatorSettings().value()));
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
}
