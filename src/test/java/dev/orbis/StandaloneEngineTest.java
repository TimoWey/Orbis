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
}
