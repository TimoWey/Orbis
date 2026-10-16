package dev.orbis.engine;

import dev.orbis.engine.mixin.MaterialSystemAccessor;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The new engine's generator, sampled without a server: everything must repeat once per width and stay continuous. */
class TorusGenerationTest {
    private static final HolderLookup.Provider REGISTRIES = VanillaRegistries.createWorldLookup();

    record Dimension(String name, ResourceKey<NoiseGeneratorSettings> settings,
                     ResourceKey<MultiNoiseBiomeSourceParameterList> biomes, int widthChunks) {}

    private static final List<Dimension> DIMENSIONS = List.of(
            new Dimension("overworld", NoiseGeneratorSettings.OVERWORLD, MultiNoiseBiomeSourceParameterLists.OVERWORLD, 1_024),
            new Dimension("nether", NoiseGeneratorSettings.NETHER, MultiNoiseBiomeSourceParameterLists.NETHER, 128),
            new Dimension("normal overworld", NoiseGeneratorSettings.OVERWORLD, MultiNoiseBiomeSourceParameterLists.OVERWORLD, 76_544),
            // The widest world: its laps reach past the game's 30 million block limit, where they are skipped.
            new Dimension("earth overworld", NoiseGeneratorSettings.OVERWORLD, MultiNoiseBiomeSourceParameterLists.OVERWORLD, 1_448_160));

    private static TorusChunkGenerator generator(Dimension d) {
        var biomes = MultiNoiseBiomeSource.createFromPreset(
                REGISTRIES.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(d.biomes()));
        return new TorusChunkGenerator(biomes, REGISTRIES.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(d.settings()), d.widthChunks());
    }

    private static RandomState random(TorusChunkGenerator generator) {
        return Tori.creatingRandomState(generator.torus(), () -> RandomState.create(
                REGISTRIES.lookupOrThrow(Registries.NOISE), 8675309L, generator.generatorSettings().value()));
    }

    @Test void columnsBiomesAndDensityRepeatAcrossSeamsAndCorners() {
        for (Dimension d : DIMENSIONS) {
            var generator = generator(d);
            var random = random(generator);
            int width = generator.torus().widthBlocks(), min = generator.torus().minBlock();
            var resolver = generator.getBiomeSource().createUncachedResolver(random);
            var height = LevelHeightAccessor.create(generator.getMinY(), generator.getGenDepth());
            for (int[] point : new int[][]{{min, min}, {min + 3, 29}, {41, min + 7}, {1234, -777}}) {
                int x = point[0], z = point[1];
                var column = generator.getBaseColumn(x, z, height, random);
                for (int[] lap : new int[][]{{1, 0}, {0, 1}, {1, 1}, {-1, -1}, {2, -3}}) {
                    long farX = x + (long) lap[0] * width, farZ = z + (long) lap[1] * width;
                    if (Math.abs(farX) > 30_000_000 || Math.abs(farZ) > 30_000_000) continue;
                    int otherX = (int) farX, otherZ = (int) farZ;
                    var repeated = generator.getBaseColumn(otherX, otherZ, height, random);
                    for (int y = height.getMinY(); y <= height.getMaxY(); y++)
                        assertEquals(column.getBlock(y), repeated.getBlock(y), d.name() + " column at y=" + y);
                    for (int y : new int[]{-40, 16, 64, 112}) {
                        assertEquals(resolver.getNoiseBiome(Math.floorDiv(x, 4), y / 4, Math.floorDiv(z, 4)),
                                resolver.getNoiseBiome(Math.floorDiv(otherX, 4), y / 4, Math.floorDiv(otherZ, 4)), d.name());
                        assertEquals(random.sampleBlockValueUncached(random.router.finalDensity(), x, y, z),
                                random.sampleBlockValueUncached(random.router.finalDensity(), otherX, y, otherZ), 1e-4f, d.name());
                    }
                }
            }
        }
    }

    /** Stepping over the seam changes density no more than the steps beside it: no cliff at the edge. */
    @Test void densityIsContinuousAcrossTheSeam() {
        for (Dimension d : DIMENSIONS) {
            var generator = generator(d);
            var random = random(generator);
            var density = random.router.finalDensity();
            int min = generator.torus().minBlock(), max = min + generator.torus().widthBlocks() - 1;
            double seam = 0, beside = 0;
            for (int z = -300; z < 300; z += 7) for (int y = -48; y < 160; y += 13) {
                seam += Math.abs(random.sampleBlockValueUncached(density, max, y, z) - random.sampleBlockValueUncached(density, min, y, z));
                seam += Math.abs(random.sampleBlockValueUncached(density, z, y, max) - random.sampleBlockValueUncached(density, z, y, min));
                beside += Math.abs(random.sampleBlockValueUncached(density, max - 1, y, z) - random.sampleBlockValueUncached(density, max, y, z));
                beside += Math.abs(random.sampleBlockValueUncached(density, z, y, min) - random.sampleBlockValueUncached(density, z, y, min + 1));
            }
            assertTrue(seam <= beside * 1.5 + 1e-3, d.name() + ": mean seam step " + seam + " vs beside " + beside);
        }
    }

    @Test void surfaceNoiseRepeats() {
        var generator = generator(DIMENSIONS.getFirst());
        var surface = (MaterialSystemAccessor) random(generator).surfaceSystem();
        int width = generator.torus().widthBlocks();
        for (int x = -5000; x < 5000; x += 997) {
            assertEquals(surface.orbis$surfaceNoise().get(x, 0, 17), surface.orbis$surfaceNoise().get(x + width, 0, 17 - width), 1e-5);
            assertEquals(surface.orbis$badlandsPillarNoise().get(x * 0.2, 0, 3 * 0.2),
                    surface.orbis$badlandsPillarNoise().get((x + width) * 0.2, 0, 3 * 0.2), 1e-5);
        }
    }

    @Test void vanillaRandomStatesStayUntouched() {
        var generator = generator(DIMENSIONS.getFirst());
        var plain = RandomState.create(REGISTRIES.lookupOrThrow(Registries.NOISE), 8675309L, generator.generatorSettings().value());
        var density = plain.router.finalDensity();
        int width = generator.torus().widthBlocks();
        boolean differs = false;
        for (int x = 0; x < 2000 && !differs; x += 101)
            differs = plain.sampleBlockValueUncached(density, x, 40, 5) != plain.sampleBlockValueUncached(density, x + width, 40, 5);
        assertTrue(differs, "a vanilla random state must not become periodic");
    }
}
