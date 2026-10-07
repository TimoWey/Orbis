package dev.orbis.debug;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AtlasWaypointsTest {
    @TempDir Path directory;
    private static DebugSnapshot.Place point(int x) { return new DebugSnapshot.Place("Waypoint", x, 64, 20); }

    @Test void persistsMarkersAndKeepsWorldsAndDimensionsSeparate() {
        Path file = directory.resolve("waypoints.properties");
        var store = new AtlasWaypoints(file);
        store.open("world-a|overworld");
        assertTrue(store.toggle(point(12), 0));
        store.open("world-a|nether");
        assertTrue(store.points().isEmpty());
        assertTrue(store.toggle(point(-5), 0));
        store.open("world-b|overworld");
        assertTrue(store.points().isEmpty());
        var reloaded = new AtlasWaypoints(file);
        reloaded.open("world-a|overworld");
        assertEquals(12, reloaded.points().getFirst().x());
        reloaded.open("world-a|nether");
        assertEquals(-5, reloaded.points().getFirst().x());
    }

    @Test void proximityRemovesMarkersAndRenumbersConsistently() {
        var store = new AtlasWaypoints(directory.resolve("waypoints.properties"));
        store.open("world|dimension");
        assertTrue(store.toggle(point(10), 0));
        assertTrue(store.toggle(point(100), 0));
        assertTrue(store.toggle(point(12), 3));
        assertEquals(1, store.points().size());
        assertEquals("Waypoint 1", store.points().getFirst().label());
        assertTrue(store.toggle(point(200), 0));
        assertEquals("Waypoint 2", store.points().getLast().label());
    }

    @Test void failedWriteDoesNotChangeVisibleMarkers() throws Exception {
        Path blockedParent = directory.resolve("file-not-directory");
        Files.writeString(blockedParent, "blocked");
        var store = new AtlasWaypoints(blockedParent.resolve("waypoints.properties"));
        store.open("world|dimension");
        assertFalse(store.toggle(point(12), 0));
        assertTrue(store.points().isEmpty());
        assertFalse(store.status().isEmpty());
    }

    @Test void legacyPathOnlyMarkersArePreservedButNeverAttachedToANewWorld() throws Exception {
        Path file = directory.resolve("waypoints.properties");
        var store = new AtlasWaypoints(file);
        store.open("legacy-save-path|dimension");
        assertTrue(store.toggle(point(12), 0));
        String identity = AtlasWorldIdentity.resolve(directory, 42);
        store.open(AtlasWorldIdentity.waypointKey(identity, "dimension"));
        assertTrue(store.points().isEmpty());
        assertTrue(store.status().contains("Legacy"));
        assertTrue(store.toggle(point(99), 0));
        store.open("legacy-save-path|dimension");
        assertEquals(12, store.points().getFirst().x(), "Unverifiable legacy records stay available for manual recovery");
        store.open(AtlasWorldIdentity.waypointKey(AtlasWorldIdentity.resolve(directory, 43), "dimension"));
        assertTrue(store.points().isEmpty());
    }

    @Test void malformedFileIsNotOverwrittenByAChange() throws Exception {
        Path file = directory.resolve("waypoints.properties");
        String invalid = "broken=\\uNOPE\n";
        Files.writeString(file, invalid);
        var store = new AtlasWaypoints(file);
        store.open("world|dimension");
        assertFalse(store.toggle(point(12), 0));
        assertFalse(store.status().isEmpty());
        assertEquals(invalid, Files.readString(file));
    }

    @Test void boundsMarkerCountAndIgnoresMalformedEntries() throws Exception {
        Path file = directory.resolve("waypoints.properties");
        Files.writeString(file, "world=bad;1,2,nope;1,64,2;\n");
        var store = new AtlasWaypoints(file);
        store.open("world");
        assertEquals(1, store.points().size());
        for (int x = 2; x <= 128; x++) assertTrue(store.toggle(point(x), 0));
        assertFalse(store.toggle(point(129), 0));
        assertEquals(128, store.points().size());
    }
}
