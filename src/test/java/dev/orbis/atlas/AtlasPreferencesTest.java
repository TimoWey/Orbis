package dev.orbis.atlas;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AtlasPreferencesTest {
    @TempDir Path directory;
    @Test void zoomSurvivesRestartAndRejectsNonFiniteOrMalformedInput() throws Exception {
        Path file = directory.resolve("atlas.properties");
        var store = new AtlasPreferences.Store(file);
        store.number("zoom.false.minecraft:overworld", 327.68);
        store.number("zoom.true.minecraft:the_nether", 2.5);
        var restarted = new AtlasPreferences.Store(file);
        assertEquals(327.68, restarted.number("zoom.false.minecraft:overworld", 512, 32, 30000000));
        assertEquals(2.5, restarted.number("zoom.true.minecraft:the_nether", 1, .5, 12));
        Files.writeString(file, "bad=NaN\nlarge=1e99\nmalformed=oops\n");
        var invalid = new AtlasPreferences.Store(file);
        assertEquals(1, invalid.number("bad", 1, .5, 12));
        assertEquals(12, invalid.number("large", 1, .5, 12));
        assertEquals(1, invalid.number("malformed", 1, .5, 12));
    }

    @Test void failedWriteIsVisibleAndCanRecoverAfterTheDirectoryIsFixed() throws Exception {
        Path parent = directory.resolve("blocked");
        Files.writeString(parent, "not a directory");
        var store = new AtlasPreferences.Store(parent.resolve("atlas.properties"));
        store.setGlobe("minecraft:overworld", true);
        assertFalse(store.status().isEmpty());
        Files.delete(parent); Files.createDirectory(parent);
        store.setGlobe("minecraft:overworld", false);
        assertTrue(store.status().isEmpty());
        assertFalse(new AtlasPreferences.Store(parent.resolve("atlas.properties")).globe("minecraft:overworld"));
    }
    @Test void malformedPreferencesAreReportedAndNotOverwritten() throws Exception {
        Path file = directory.resolve("atlas.properties");
        String invalid = "broken=\\uNOPE\n";
        Files.writeString(file, invalid);
        var store = new AtlasPreferences.Store(file);
        assertFalse(store.status().isEmpty());
        store.setGlobe("minecraft:overworld", true);
        assertEquals(invalid, Files.readString(file));
    }
    @Test void viewModesSurviveRestartIndependentlyForEveryDimension() throws Exception {
        Path file = directory.resolve("atlas.properties");
        var store = new AtlasPreferences.Store(file);
        store.setGlobe("minecraft:overworld", true);
        store.setGlobe("minecraft:the_nether", false);
        store.setGlobe("minecraft:the_end", false);
        store.setGlobe("mod:moon", true);
        var restarted = new AtlasPreferences.Store(file);
        assertTrue(restarted.globe("minecraft:overworld"));
        assertFalse(restarted.globe("minecraft:the_nether"));
        assertFalse(restarted.globe("minecraft:the_end"));
        assertTrue(restarted.globe("mod:moon"));
        restarted.setGlobe("minecraft:the_nether", true);
        restarted.setGlobe("minecraft:overworld", false);
        var again = new AtlasPreferences.Store(file);
        assertTrue(again.globe("minecraft:the_nether"));
        assertFalse(again.globe("minecraft:overworld"));
        assertFalse(again.globe("minecraft:the_end"));
    }
    @Test void legacyDefaultMigratesWithoutOverwritingOtherDimensionsOrHudSettings() throws Exception {
        Path file = directory.resolve("atlas.properties");
        Files.writeString(file, "globe=true\ncorner=2\nsize=0\n");
        var store = new AtlasPreferences.Store(file);
        assertTrue(store.globe("minecraft:overworld"));
        store.setGlobe("minecraft:the_nether", false);
        var restarted = new AtlasPreferences.Store(file);
        assertFalse(restarted.globe("minecraft:the_nether"));
        assertTrue(restarted.globe("minecraft:overworld"));
        assertTrue(Files.readString(file).contains("corner=2"));
        assertTrue(Files.readString(file).contains("size=0"));
    }
}
