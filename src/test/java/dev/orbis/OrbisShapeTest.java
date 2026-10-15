package dev.orbis;

import java.util.HashMap;
import dev.orbis.engine.TorusChunkGenerator;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrbisShapeTest {
    private static HolderLookup.Provider registries;
    @BeforeAll static void setup() {
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
        assertEquals(WorldSize.select(128), OrbisShape.read(new WorldDimensions(stems)));
    }

    @Test void ordinaryWorldIsNeverClaimedAndStripsToItself() {
        var ordinary = vanilla();
        assertNull(OrbisShape.read(ordinary));
        assertTrue(OrbisShape.fits(ordinary));
        assertEquals(ordinary.dimensions(), OrbisShape.strip(ordinary).dimensions());
    }

    @Test void strippingAnOrbisWorldGivesBackItsNoiseGenerators() {
        var stripped = OrbisShape.strip(OrbisShape.apply(vanilla(), WorldSize.DEFAULT));
        assertNull(OrbisShape.read(stripped));
        for (var key : java.util.List.of(LevelStem.OVERWORLD, LevelStem.NETHER)) {
            var generator = stripped.get(key).orElseThrow().generator();
            assertEquals(NoiseBasedChunkGenerator.class, generator.getClass());
            var original = (NoiseBasedChunkGenerator) vanilla().get(key).orElseThrow().generator();
            assertEquals(original.generatorSettings(), ((NoiseBasedChunkGenerator) generator).generatorSettings());
        }
        // Re-created at another size, the world is still exactly one Orbis world.
        assertEquals(WorldSize.select(256), OrbisShape.read(OrbisShape.apply(stripped, WorldSize.select(256))));
    }

    @Test void worldShapeFollowsTheWorldBeingRecreated() {
        try {
            OrbisWorldShape.reset(OrbisShape.apply(vanilla(), WorldSize.select(128)));
            assertTrue(OrbisWorldShape.orbis());
            assertEquals(WorldSize.select(128), OrbisWorldShape.size());
            OrbisWorldShape.orbis(false);
            assertNull(OrbisShape.read(OrbisWorldShape.apply(OrbisShape.apply(vanilla(), WorldSize.select(128)))));
            OrbisWorldShape.reset(vanilla());
            assertFalse(OrbisWorldShape.orbis());
            assertEquals(WorldSize.DEFAULT, OrbisWorldShape.size());
            OrbisWorldShape.orbis(true);
            assertEquals(WorldSize.DEFAULT, OrbisShape.read(OrbisWorldShape.apply(vanilla())));
        } finally {
            OrbisWorldShape.reset(vanilla());
        }
    }

    @Test void customDimensionsStayAsTheyAre() {
        var customKey = ResourceKey.create(Registries.LEVEL_STEM, Identifier.fromNamespaceAndPath("test", "moon"));
        var custom = stem(BuiltinDimensionTypes.OVERWORLD, NoiseGeneratorSettings.OVERWORLD);
        var entries = new HashMap<>(vanilla().dimensions());
        entries.put(customKey, custom);
        var created = OrbisShape.apply(new WorldDimensions(entries), WorldSize.DEFAULT);
        assertSame(custom, created.get(customKey).orElseThrow());
        assertSame(vanilla().get(LevelStem.END).orElseThrow().generator().getClass(),
                created.get(LevelStem.END).orElseThrow().generator().getClass());
    }

    @Test void superflatCannotBecomeOrbis() {
        var flat = new HashMap<>(vanilla().dimensions());
        var overworld = flat.get(LevelStem.OVERWORLD);
        flat.put(LevelStem.OVERWORLD, new LevelStem(overworld.type(), new net.minecraft.world.level.levelgen.FlatLevelSource(
                net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings.getDefault(
                        registries.lookupOrThrow(Registries.BIOME), registries.lookupOrThrow(Registries.STRUCTURE_SET),
                        registries.lookupOrThrow(Registries.PLACED_FEATURE)))));
        var dimensions = new WorldDimensions(flat);
        assertFalse(OrbisShape.fits(dimensions));
        assertThrows(IllegalArgumentException.class, () -> OrbisShape.apply(dimensions, WorldSize.DEFAULT));
    }

    @Test void readerRejectsIncorrectNetherCoordinateScale() {
        var created = OrbisShape.apply(vanilla(), WorldSize.DEFAULT);
        var entries = new HashMap<>(created.dimensions());
        entries.put(LevelStem.NETHER, new LevelStem(created.get(LevelStem.OVERWORLD).orElseThrow().type(),
                created.get(LevelStem.NETHER).orElseThrow().generator()));
        assertNull(OrbisShape.read(new WorldDimensions(entries)));
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
