package dev.orbis;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StandaloneEngineTest {
    @BeforeAll static void initializeRealEntrypoint() {
        new Orbis().onInitialize();
    }

    @Test void loadsOnItsOwn() {
        assertTrue(FabricLoader.getInstance().isModLoaded("orbis"));
        assertFalse(FabricLoader.getInstance().isModLoaded("toroidal_world"));
        assertEquals(System.getProperty("orbis.expectedVersion"), OrbisRequired.version());
    }

    @Test void registersOnlyTheOrbisGenerator() {
        assertTrue(BuiltInRegistries.CHUNK_GENERATOR.containsKey(Identifier.fromNamespaceAndPath("orbis", "torus")));
        assertTrue(BuiltInRegistries.CHUNK_GENERATOR.keySet().stream().noneMatch(id -> id.getNamespace().equals("toroidal_world")));
    }

    @Test void dedicatedServerPresetsDecodeAndKeepTheirSizeOnSaveReload() throws Exception {
        var registries = net.minecraft.data.registries.VanillaRegistries.createWorldLookup();
        var ops = registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var codec = net.minecraft.world.level.levelgen.presets.WorldPreset.DIRECT_CODEC;
        for (var preset : WorldSize.Preset.values()) {
            String path = "/data/orbis/worldgen/world_preset/" + preset.name().toLowerCase(java.util.Locale.ROOT) + ".json";
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
                var decoded = codec.parse(ops, json).getOrThrow();
                assertEquals(preset.size, OrbisShape.read(decoded.createWorldDimensions()), path);
                var saved = codec.encodeStart(ops, decoded).getOrThrow();
                assertEquals(preset.size, OrbisShape.read(codec.parse(ops, saved).getOrThrow().createWorldDimensions()));
            }
        }
    }
}
