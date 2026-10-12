package dev.orbis;

import com.toroidalworld.platform.FabricPlatform;
import com.toroidalworld.settings.SettingsService;
import com.toroidalworld.shape.WorldShapes;
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

    @Test void loadsWithoutASeparateToroidalWorldMod() {
        assertTrue(FabricLoader.getInstance().isModLoaded("orbis"));
        assertFalse(FabricLoader.getInstance().isModLoaded("toroidal_world"));
        assertEquals(System.getProperty("orbis.expectedVersion"), new FabricPlatform().modVersion());
    }

    @Test void registersEngineCodecsUnderTheExistingSaveNamespace() {
        assertTrue(BuiltInRegistries.CHUNK_GENERATOR.containsKey(
                Identifier.fromNamespaceAndPath("toroidal_world", "toroidal")));
    }

    @Test void exposesOnlyNormalAndOrbisWorldCreationShapes() {
        assertEquals(2, WorldShapes.shapes().size());
        assertTrue(WorldShapes.shapes().stream().anyMatch(shape -> shape.id().equals(OrbisShape.MODULE.id())));
        assertEquals("orbis-engine.json", SettingsService.FILE_NAME);
    }

    @Test void dedicatedServerPresetsDecodeAndPreserveOrbisOnSaveReload() throws Exception {
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
