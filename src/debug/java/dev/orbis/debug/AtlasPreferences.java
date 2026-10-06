package dev.orbis.debug;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Small client-only preferences; no world save data is modified. */
final class AtlasPreferences {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("orbis-atlas.properties");
    private static final Properties VALUES = new Properties();
    static {
        if (Files.isRegularFile(FILE)) try (Reader reader = Files.newBufferedReader(FILE)) { VALUES.load(reader); }
        catch (IOException | IllegalArgumentException ignored) { }
    }
    static boolean globe() { return Boolean.parseBoolean(VALUES.getProperty("globe", "false")); }
    static void setGlobe(boolean globe) { VALUES.setProperty("globe", Boolean.toString(globe)); save(); }
    static int corner() {
        try { return Math.floorMod(Integer.parseInt(VALUES.getProperty("corner", "0")), 4); }
        catch (NumberFormatException ignored) { return 0; }
    }
    static void cycleCorner() { VALUES.setProperty("corner", Integer.toString((corner() + 1) % 4)); save(); }
    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) { VALUES.store(writer, "Orbis atlas"); }
        } catch (IOException ignored) { }
    }
    private AtlasPreferences() { }
}
