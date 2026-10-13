package dev.orbis;

import com.toroidalworld.api.v1.option.WorldOptions;
import com.toroidalworld.core.ShapedChunkGenerator;
import com.toroidalworld.engine.noise.GenerationTransformerContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Real vanilla density/biome/column generation, without a running server or renderer. */
class OrbisGenerationTest {
    @Test void terrainColumnsAndBiomesRepeatAcrossBothSeamsAndCorners() {
        if (!WorldOptions.all().contains(OrbisShape.ENABLED)) WorldOptions.register(OrbisShape.ENABLED);
        var registries = VanillaRegistries.createWorldLookup();
        var size = WorldSize.Preset.TINY.size;
        var dimensions = OrbisShape.applyLegacy(WorldPresets.createNormalWorldDimensions(registries), size);
        for (var dimension : java.util.List.of(LevelStem.OVERWORLD, LevelStem.NETHER)) {
            var generator = (NoiseBasedChunkGenerator) dimensions.get(dimension).orElseThrow().generator();
            var fold = ((ShapedChunkGenerator) generator).transformer();
            var random = GenerationTransformerContext.withRouterBuild(fold, () -> RandomState.create(
                    registries.lookupOrThrow(Registries.NOISE), 8675309L, generator.generatorSettings().value()));
            var resolver = generator.getBiomeSource().createUncachedResolver(random);
            int width = dimension == LevelStem.OVERWORLD ? size.overworldBlocks() : size.netherBlocks();
            var height = LevelHeightAccessor.create(generator.getMinY(), generator.getGenDepth());
            for (int[] point : new int[][]{{-width / 2, -width / 2}, {-width / 2 + 3, 29}, {41, -width / 2 + 7}}) {
                int x = point[0], z = point[1];
                var column = generator.getBaseColumn(x, z, height, random);
                for (int[] lap : new int[][]{{1, 0}, {0, 1}, {1, 1}, {-1, -1}}) {
                    int otherX = x + lap[0] * width, otherZ = z + lap[1] * width;
                    var repeated = generator.getBaseColumn(otherX, otherZ, height, random);
                    for (int y = height.getMinY(); y <= height.getMaxY(); y++) {
                        assertEquals(column.getBlock(y), repeated.getBlock(y),
                                "Generated column must repeat in " + dimension.identifier() + " at y=" + y);
                    }
                    for (int y : new int[]{16, 64, 112}) {
                        assertEquals(resolver.getNoiseBiome(Math.floorDiv(x, 4), y / 4, Math.floorDiv(z, 4)),
                                resolver.getNoiseBiome(Math.floorDiv(otherX, 4), y / 4, Math.floorDiv(otherZ, 4)));
                        assertEquals(random.sampleBlockValueUncached(random.router.finalDensity(), x, y, z),
                                random.sampleBlockValueUncached(random.router.finalDensity(), otherX, y, otherZ), 0.00001f);
                    }
                }
            }
        }
    }
}
