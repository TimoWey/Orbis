package dev.orbis.debug;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

/** Client markers scoped by save directory and dimension, persisted outside the save. */
final class AtlasWaypoints {
    static final AtlasWaypoints SHARED = new AtlasWaypoints(FabricLoader.getInstance().getConfigDir().resolve("orbis-waypoints.properties"));
    private final Path file;
    private final Properties values = new Properties();
    private String key;
    private List<DebugSnapshot.Place> points = List.of();
    AtlasWaypoints(Path file) { this.file = file; }
    void open(Minecraft client, String dimension) {
        var server = client.getSingleplayerServer();
        open(server == null ? null : server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize() + "|" + dimension);
    }
    void open(String worldKey) {
        key = worldKey;
        values.clear();
        if (Files.isRegularFile(file)) try (Reader reader = Files.newBufferedReader(file)) { values.load(reader); }
        catch (IOException | IllegalArgumentException ignored) { }
        List<DebugSnapshot.Place> loaded = new ArrayList<>();
        for (String entry : (key == null ? "" : values.getProperty(key, "")).split(";")) {
            String[] parts = entry.split(",");
            if (parts.length != 3 || loaded.size() >= 128) continue;
            try { loaded.add(new DebugSnapshot.Place("Waypoint " + (loaded.size() + 1), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]))); }
            catch (NumberFormatException ignored) { }
        }
        points = List.copyOf(loaded);
    }
    List<DebugSnapshot.Place> points() { return points; }
    boolean toggle(DebugSnapshot.Place point, double tolerance) {
        if (key == null) return false;
        List<DebugSnapshot.Place> next = new ArrayList<>(points);
        boolean removed = next.removeIf(p -> Math.hypot((double) p.x() - point.x(), (double) p.z() - point.z()) <= tolerance);
        if (!removed) {
            if (next.size() >= 128) return false;
            next.add(point);
        }
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < next.size(); i++) {
            var p = next.get(i);
            next.set(i, new DebugSnapshot.Place("Waypoint " + (i + 1), p.x(), p.y(), p.z()));
            encoded.append(p.x()).append(',').append(p.y()).append(',').append(p.z()).append(';');
        }
        Properties saved = new Properties();
        saved.putAll(values);
        saved.setProperty(key, encoded.toString());
        Path temporary = null;
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "orbis-waypoints-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) { saved.store(writer, "Orbis local waypoints"); }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            values.clear(); values.putAll(saved);
            points = List.copyOf(next);
            return true;
        } catch (IOException ignored) { return false; }
        finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
}
