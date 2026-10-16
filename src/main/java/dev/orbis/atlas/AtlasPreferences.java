package dev.orbis.atlas;

import dev.orbis.Orbis;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Small client-only preferences; no world save data is modified. */
final class AtlasPreferences {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("orbis-atlas.properties");
    private static final Store STORE = new Store(FILE);
    private static final Properties VALUES = STORE.values;
    static boolean globe(String dimension) { return STORE.globe(dimension); }
    static void setGlobe(String dimension, boolean globe) { STORE.setGlobe(dimension, globe); }
    /** Whether the full map continues past the world's edges, or shows the world once as a square. */
    static boolean repeat() { return Boolean.parseBoolean(VALUES.getProperty("map.repeat", "true")); }
    static void toggleRepeat() { VALUES.setProperty("map.repeat", Boolean.toString(!repeat())); save(); }
    static boolean visible() { return Boolean.parseBoolean(VALUES.getProperty("visible", "true")); }
    static void toggleVisible() { VALUES.setProperty("visible", Boolean.toString(!visible())); save(); }
    static double minimapZoom() { return STORE.number("minimap.zoom", 1, .25, 8); }
    static void cycleMinimapZoom() { STORE.number("minimap.zoom", minimapZoom() >= 8 ? .25 : minimapZoom() * 2); }
    static double zoom(String dimension, boolean globe) { return STORE.number("zoom." + globe + "." + dimension, globe ? 1 : 512, globe ? .5 : 32, globe ? 12 : 30000000); }
    static void zoom(String dimension, boolean globe, double value) { STORE.number("zoom." + globe + "." + dimension, value); }
    /** One write when the atlas closes or changes dimension, not one per scroll step. */
    static void saveZoom(String dimension, double flat, double globe) { STORE.zoom(dimension, flat, globe); }
    static String status() { return STORE.status(); }
    static int corner() {
        try { return Math.floorMod(Integer.parseInt(VALUES.getProperty("corner", "0")), 4); }
        catch (NumberFormatException ignored) { return 0; }
    }
    static void cycleCorner() { VALUES.setProperty("corner", Integer.toString((corner() + 1) % 4)); save(); }
    static int sizeIndex() {
        try { return Math.floorMod(Integer.parseInt(VALUES.getProperty("size", "1")), 3); }
        catch (NumberFormatException ignored) { return 1; }
    }
    static int size() { return new int[] {72, 96, 128}[sizeIndex()]; }
    static void cycleSize() { VALUES.setProperty("size", Integer.toString((sizeIndex() + 1) % 3)); save(); }
    private static void save() { STORE.save(); }

    /** Separate file-backed store keeps dimension persistence testable without touching user settings. */
    static final class Store {
        private final Path file;
        private final Properties values = new Properties();
        private String status = "";
        private boolean readable = true;
        Store(Path file) {
            this.file = file.toAbsolutePath();
            if (Files.exists(file)) try (Reader reader = Files.newBufferedReader(file)) { values.load(reader); }
            catch (IOException | IllegalArgumentException failure) {
                values.clear();
                readable = false;
                status = "Preferences could not be loaded; saving disabled";
                Orbis.LOGGER.warn("Could not load Orbis atlas preferences {}", file, failure);
            }
        }
        double number(String key, double fallback, double min, double max) {
            try { double value = Double.parseDouble(values.getProperty(key, Double.toString(fallback)));
                return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
            } catch (NumberFormatException ignored) { return fallback; }
        }
        void number(String key, double value) {
            if (!Double.isFinite(value)) return;
            values.setProperty(key, Double.toString(value)); save();
        }
        void zoom(String dimension, double flat, double globe) {
            if (dimension == null || !Double.isFinite(flat) || !Double.isFinite(globe)) return;
            values.setProperty("zoom.false." + dimension, Double.toString(flat));
            values.setProperty("zoom.true." + dimension, Double.toString(globe)); save();
        }
        String status() { return status; }
        boolean globe(String dimension) {
            return Boolean.parseBoolean(values.getProperty("globe." + dimension, values.getProperty("globe", "false")));
        }
        void setGlobe(String dimension, boolean globe) {
            if (dimension == null) return;
            values.setProperty("globe." + dimension, Boolean.toString(globe)); save();
        }
        private void save() {
            if (!readable) return;
            Path temporary = null;
            try {
                Files.createDirectories(file.getParent());
                temporary = Files.createTempFile(file.getParent(), "orbis-atlas-", ".tmp");
                try (Writer writer = Files.newBufferedWriter(temporary)) { values.store(writer, "Orbis atlas; globe per dimension"); }
                try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
                status = "";
            } catch (IOException failure) {
                status = "Preferences could not be saved";
                Orbis.LOGGER.warn("Could not save Orbis atlas preferences {}", file, failure);
            }
            finally {
                if (temporary != null) try { Files.deleteIfExists(temporary); }
                catch (IOException failure) { Orbis.LOGGER.warn("Could not remove temporary atlas preferences {}", temporary, failure); }
            }
        }
    }
    private AtlasPreferences() { }
}
