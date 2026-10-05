package dev.orbis;

import java.util.HashMap;
import java.util.Map;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.toroidalworld.api.v1.option.WorldOptions;
import com.toroidalworld.api.v1.shape.LoopSpans;
import com.toroidalworld.api.v1.shape.ShapeDimensions;
import com.toroidalworld.core.ShapedChunkGenerator;
import com.toroidalworld.core.ToroidalShapeView;
import com.toroidalworld.engine.gen.LoopedChunkGenerator;
import com.toroidalworld.shape.torus.TorusDimensions;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrbisShapeTest {
    private static HolderLookup.Provider registries;
    @BeforeAll static void setup() {
        if (!WorldOptions.all().contains(OrbisShape.ENABLED)) WorldOptions.register(OrbisShape.ENABLED);
        registries = VanillaRegistries.createWorldLookup();
    }

    @Test void dimensionsUseExactBoundsAndAnUnwrappedEnd() {
        for (WorldSize.Preset preset : WorldSize.Preset.values()) {
            WorldDimensions result = OrbisShape.apply(vanilla(), preset.size);
            assertEquals(preset.size, OrbisShape.read(result));
            assertEquals(LoopSpans.ofWidth(preset.size.netherChunks()), ShapeDimensions.spansOf(result, LevelStem.NETHER));
            assertEquals(LoopSpans.NONE, ShapeDimensions.spansOf(result, LevelStem.END));
            var end = (ShapedChunkGenerator) result.get(LevelStem.END).orElseThrow().generator();
            var shape = new ToroidalShapeView(end.transformer());
            assertFalse(shape.loops(Direction.Axis.X));
            assertFalse(shape.loops(Direction.Axis.Z));
            var pos = new Vec3(8_000_000, 70, -9_000_000);
            assertSame(pos, shape.fold(pos));
        }
    }

    @Test void engineWrapsXAndZAndOffsetDiagonalsWithoutChangingHeight() {
        var result = OrbisShape.apply(vanilla(), new WorldSize(128));
        var generator = (ShapedChunkGenerator) result.get(LevelStem.OVERWORLD).orElseThrow().generator();
        var shape = new ToroidalShapeView(generator.transformer());
        var start = new Vec3(512, 73.25, 819.2);
        assertEquals(start, shape.fold(start.add(2048, 0, 0)));
        assertEquals(start.z, shape.fold(start.add(0, 0, 2048)).z, 1e-8);
        assertEquals(start.x, shape.fold(start.add(-4096, 0, -4096)).x, 1e-8);
        assertEquals(start.z, shape.fold(start.add(-4096, 0, -4096)).z, 1e-8);
        assertEquals(start.y, shape.fold(start.add(2048, 0, 2048)).y);
        assertEquals(-1024.0, shape.foldCoord(Direction.Axis.X, 1024.0));
        assertEquals(1023.5, shape.foldCoord(Direction.Axis.Z, -1024.5));
    }

    @Test void optionsAndBoundsSurviveGeneratorCodecRoundTrip() {
        var created = OrbisShape.apply(vanilla(), WorldSize.DEFAULT);
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var stems = new HashMap<>(created.dimensions());
        for (var key : java.util.List.of(LevelStem.OVERWORLD, LevelStem.NETHER, LevelStem.END)) {
            var stem = created.get(key).orElseThrow();
            JsonElement encoded = LoopedChunkGenerator.CODEC.codec()
                    .encodeStart(ops, (LoopedChunkGenerator) stem.generator()).getOrThrow();
            var decoded = LoopedChunkGenerator.CODEC.codec().parse(ops, encoded).getOrThrow();
            stems.put(key, new LevelStem(stem.type(), decoded));
        }
        assertEquals(WorldSize.DEFAULT, OrbisShape.read(new WorldDimensions(stems)));
    }

    @Test void ordinaryWorldIsNeverClaimedOrChangedByReading() {
        var ordinary = vanilla();
        assertNull(OrbisShape.read(ordinary));
        assertFalse(OrbisShape.owns(ordinary));
    }

    @Test void recreateKeepsOrbisOwnershipInsteadOfGenericTorus() {
        var created = OrbisShape.apply(vanilla(), WorldSize.DEFAULT);
        assertNull(TorusDimensions.read(created), "The ownership mixin must be applied by Fabric Loader");
        assertEquals(WorldSize.DEFAULT, OrbisShape.read(created));
    }

    private static WorldDimensions vanilla() {
        return new WorldDimensions(Map.of(
                LevelStem.OVERWORLD, stem(BuiltinDimensionTypes.OVERWORLD, NoiseGeneratorSettings.OVERWORLD),
                LevelStem.NETHER, stem(BuiltinDimensionTypes.NETHER, NoiseGeneratorSettings.NETHER),
                LevelStem.END, stem(BuiltinDimensionTypes.END, NoiseGeneratorSettings.END)));
    }
    private static LevelStem stem(ResourceKey<DimensionType> type, ResourceKey<NoiseGeneratorSettings> noise) {
        return new LevelStem(registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(type),
                new NoiseBasedChunkGenerator(new FixedBiomeSource(registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS)),
                        registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(noise)));
    }
}
