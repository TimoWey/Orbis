package dev.orbis;

import java.util.HashMap;
import dev.orbis.engine.TorusChunkGenerator;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.toroidalworld.api.v1.option.WorldOptions;
import com.toroidalworld.api.v1.shape.LoopSpans;
import com.toroidalworld.api.v1.shape.ShapeDimensions;
import com.toroidalworld.core.ShapedChunkGenerator;
import com.toroidalworld.core.ToroidalShapeView;
import com.toroidalworld.engine.gen.LoopedChunkGenerator;
import com.toroidalworld.shape.WorldShapes;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
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

    @Test void newWorldsRunOnTheOrbisEngineAtEverySizeIncludingDebug() {
        var sizes = new java.util.ArrayList<WorldSize>();
        for (WorldSize.Preset preset : WorldSize.Preset.values()) sizes.add(preset.size);
        sizes.add(WorldSize.select(WorldSize.DEBUG_MIN_CHUNKS));
        sizes.add(WorldSize.select(144));
        for (WorldSize size : sizes) {
            WorldDimensions result = OrbisShape.apply(vanilla(), size);
            var overworld = (TorusChunkGenerator) result.get(LevelStem.OVERWORLD).orElseThrow().generator();
            var nether = (TorusChunkGenerator) result.get(LevelStem.NETHER).orElseThrow().generator();
            assertEquals(size.overworldChunks(), overworld.torus().widthChunks());
            assertEquals(size.netherChunks(), nether.torus().widthChunks());
            assertEquals(overworld.torus().nether(), nether.torus());
            assertFalse(result.get(LevelStem.END).orElseThrow().generator() instanceof TorusChunkGenerator);
            assertEquals(size, OrbisShape.read(result));
        }
        var debug = (TorusChunkGenerator) OrbisShape.apply(vanilla(), WorldSize.select(128)).get(LevelStem.NETHER).orElseThrow().generator();
        assertEquals(16, debug.torus().widthChunks());
        assertEquals(-128, debug.torus().minBlock());
    }

    @Test void engineWorldSurvivesGeneratorCodecRoundTripAndRestoresAsOrbis() {
        var created = OrbisShape.apply(vanilla(), WorldSize.select(128));
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var stems = new HashMap<>(created.dimensions());
        for (var key : java.util.List.of(LevelStem.OVERWORLD, LevelStem.NETHER)) {
            var stem = created.get(key).orElseThrow();
            JsonElement encoded = TorusChunkGenerator.CODEC.codec().encodeStart(ops, (TorusChunkGenerator) stem.generator()).getOrThrow();
            stems.put(key, new LevelStem(stem.type(), TorusChunkGenerator.CODEC.codec().parse(ops, encoded).getOrThrow()));
        }
        var decoded = new WorldDimensions(stems);
        assertEquals(WorldSize.select(128), OrbisShape.read(decoded));
        OrbisShape.MODULE.register();
        WorldShapes.resetToDefault();
        try {
            WorldShapes.restoreFromExisting(RegistryAccess.EMPTY, decoded);
            assertEquals(OrbisShape.MODULE.id(), WorldShapes.selected().id());
            assertEquals(WorldSize.select(128), OrbisShape.MODULE.settings());
        } finally {
            WorldShapes.resetToDefault();
        }
    }

    @Test void dimensionsUseExactBoundsAndAnUnwrappedEnd() {
        for (WorldSize.Preset preset : WorldSize.Preset.values()) {
            WorldDimensions result = OrbisShape.applyLegacy(vanilla(), preset.size);
            assertEquals(preset.size, OrbisShape.read(result));
            assertEquals(LoopSpans.ofWidth(preset.size.netherChunks()), ShapeDimensions.spansOf(result, LevelStem.NETHER));
            // The public reader hides non-wrapping shapes; inspect the actual
            // generator below to ensure its explicit identity survives.
            assertNull(ShapeDimensions.spansOf(result, LevelStem.END));
            var end = (ShapedChunkGenerator) result.get(LevelStem.END).orElseThrow().generator();
            assertFalse(end.carriedShape().fold().isWrapped());
            var shape = new ToroidalShapeView(end.transformer());
            assertFalse(shape.loops(Direction.Axis.X));
            assertFalse(shape.loops(Direction.Axis.Z));
            var pos = new Vec3(8_000_000, 70, -9_000_000);
            assertSame(pos, shape.fold(pos));
        }
    }

    @Test void engineWrapsXAndZAndOffsetDiagonalsWithoutChangingHeight() {
        var result = OrbisShape.applyLegacy(vanilla(), new WorldSize(128));
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
        var created = OrbisShape.applyLegacy(vanilla(), WorldSize.DEFAULT);
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
        var baked = ordinary.bake(stemRegistry(ordinary));
        ordinary.dimensions().forEach((key, stem) -> {
            var restored = baked.dimensions().getOptional(key).orElseThrow();
            assertSame(stem.generator(), restored.generator());
            assertNull(ShapedChunkGenerator.carriedShapeOf(restored.generator()));
        });
    }

    @Test void recreateKeepsOrbisOwnershipInsteadOfGenericTorus() {
        var created = OrbisShape.applyLegacy(vanilla(), WorldSize.DEFAULT);
        OrbisShape.MODULE.register();
        WorldShapes.resetToDefault();
        try {
            WorldShapes.restoreFromExisting(RegistryAccess.EMPTY, created);
            assertEquals(OrbisShape.MODULE.id(), WorldShapes.selected().id());
            assertEquals(WorldSize.DEFAULT, OrbisShape.MODULE.settings());
        } finally {
            WorldShapes.resetToDefault();
        }
    }

    @Test void bakePreservesAnUnwrappedCustomDimensionAndEnd() {
        var customKey = ResourceKey.create(Registries.LEVEL_STEM, Identifier.fromNamespaceAndPath("test", "moon"));
        var custom = stem(BuiltinDimensionTypes.OVERWORLD, NoiseGeneratorSettings.OVERWORLD);
        var entries = new HashMap<>(vanilla().dimensions());
        entries.put(customKey, custom);
        var original = new WorldDimensions(entries);
        var baked = OrbisShape.applyLegacy(original, WorldSize.DEFAULT).bake(stemRegistry(original));
        var moon = baked.dimensions().getOptional(customKey).orElseThrow();
        assertSame(custom.generator(), moon.generator());
        assertNull(ShapedChunkGenerator.carriedShapeOf(moon.generator()));
        assertEquals(WorldSize.DEFAULT, OrbisShape.read(new WorldDimensions(baked.dimensions())));
        var end = baked.dimensions().getOptional(LevelStem.END).orElseThrow();
        // Bake may retain the unshaped datapack End; both representations must stay unwrapped.
        var endShape = ShapedChunkGenerator.carriedShapeOf(end.generator());
        assertTrue(endShape == null || !endShape.fold().isWrapped());
    }

    @Test void bakeRejectsDatapackChangingStoredNetherCoordinateScale() {
        var saved = OrbisShape.applyLegacy(vanilla(), WorldSize.DEFAULT);
        var replacement = new HashMap<>(vanilla().dimensions());
        replacement.put(LevelStem.NETHER, stem(BuiltinDimensionTypes.OVERWORLD, NoiseGeneratorSettings.NETHER));
        assertThrows(IllegalArgumentException.class, () -> saved.bake(stemRegistry(new WorldDimensions(replacement))));
    }

    @Test void readerRejectsIncorrectNetherCoordinateScale() {
        var created = OrbisShape.applyLegacy(vanilla(), WorldSize.DEFAULT);
        var entries = new HashMap<>(created.dimensions());
        entries.put(LevelStem.NETHER, new LevelStem(created.get(LevelStem.OVERWORLD).orElseThrow().type(),
                created.get(LevelStem.NETHER).orElseThrow().generator()));
        assertNull(OrbisShape.read(new WorldDimensions(entries)));
    }

    private static Registry<LevelStem> stemRegistry(WorldDimensions dimensions) {
        var registry = new MappedRegistry<LevelStem>(Registries.LEVEL_STEM, Lifecycle.stable());
        dimensions.dimensions().forEach((key, value) -> registry.register(key, value, RegistrationInfo.BUILT_IN));
        return registry.freeze();
    }

    private static WorldDimensions vanilla() {
        return WorldPresets.createNormalWorldDimensions(registries);
    }
    private static LevelStem stem(ResourceKey<DimensionType> type, ResourceKey<NoiseGeneratorSettings> noise) {
        return new LevelStem(registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(type),
                new NoiseBasedChunkGenerator(new FixedBiomeSource(registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS)),
                        registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(noise)));
    }
}
