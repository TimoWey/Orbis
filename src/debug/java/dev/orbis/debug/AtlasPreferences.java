package dev.orbis.debug;

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
