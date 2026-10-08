package dev.orbis.debug;

import java.nio.file.Path;
import java.util.*;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AtlasMinimapCacheTest {
    @TempDir Path directory;
    private static DebugSnapshot.Cell tile(int x, int z) {
        int[] colors = new int[256];
        for (int i = 0; i < colors.length; i++) colors[i] = 0xff123400 | i;
        return new DebugSnapshot.Cell(x, z, colors[0], colors);
    }
    private static AtlasMinimapCache.Frame prepare(AtlasMinimapCache cache, DebugSnapshot data, double x, double z) {
        cache.update(data, x, z, 256);
        return cache.update(data, x, z, 256);
    }

    @Test void movingWithinThePaddingReusesTheSameFullResolutionImage() {
        var data = AtlasFixtures.world(Map.of(), List.of(tile(-1, -1), tile(0, 0)), Set.of());
        try (var cache = new AtlasMinimapCache(Runnable::run)) {
            var first = prepare(cache, data, 0, 0);
            assertNotNull(first);
            for (double x = -40; x <= 40; x += .25) assertSame(first, cache.update(data, x, -x, 256));
            var window = first.window();
            for (var cell : data.chunks()) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int index = (cell.z() * 16 + z - window.z()) * window.size() + cell.x() * 16 + x - window.x();
                assertEquals(cell.colorAt(x, z), first.pixels()[index]);
            }
            assertEquals(256f / window.size(), window.u(128) - window.u(-128), 1e-6);
        }
    }

    @Test void diskAndRasterWorkWaitForTheWorkerAndAnOldFrameBridgesAWindowBoundary() throws Exception {
        var writer = new AtlasTileArchive(directory);
        writer.write(tile(0, 0), false); writer.close();
        var archive = new AtlasTileArchive(directory);
        var data = AtlasFixtures.archive(AtlasFixtures.world(Map.of(), List.of(), Set.of()), archive);
        var jobs = new ArrayDeque<Runnable>();
        try (var cache = new AtlasMinimapCache(jobs::add)) {
            assertNull(cache.update(data, 0, 0, 256));
            try (var resident = archive.residentReader()) { assertNull(resident.get(0, 0)); }
            assertEquals(1, jobs.size());
            jobs.remove().run();
            var first = cache.update(data, 0, 0, 256);
            assertNotNull(first);
            assertSame(first, cache.update(data, 49, 0, 256));
            jobs.remove().run();
            var next = cache.update(data, 49, 0, 256);
            assertNotSame(first, next);
            assertTrue(next.window().contains(49, 0, 256, 16));
            // Teleporting must never stretch or misplace old terrain while a new image is pending.
            assertNull(cache.update(data, -900, -900, 256));
        }
        archive.close();
    }

    @Test void localWritesRefreshTheImageButDistantWritesDoNot() throws Exception {
        var archive = new AtlasTileArchive(directory);
        archive.write(tile(0, 0), false);
        var data = AtlasFixtures.archive(AtlasFixtures.world(Map.of(), List.of(), Set.of()), archive);
        var jobs = new ArrayDeque<Runnable>();
        try (var cache = new AtlasMinimapCache(jobs::add)) {
            cache.update(data, 0, 0, 256); jobs.remove().run();
            var first = cache.update(data, 0, 0, 256);
            archive.write(tile(1000, 1000), false);
            assertSame(first, cache.update(data, 0, 0, 256));
            assertTrue(jobs.isEmpty());
            archive.write(tile(1, 0), false);
            assertSame(first, cache.update(data, 0, 0, 256));
            jobs.remove().run();
            assertNotSame(first, cache.update(data, 0, 0, 256));
            // Closing/reopening cannot publish a late result, even for the same archive.
            cache.update(data, 100, 0, 256);
            cache.close();
            jobs.remove().run();
            assertNull(cache.update(data, 100, 0, 256));
            jobs.remove().run();
            assertNotNull(cache.update(data, 100, 0, 256));
        }
        archive.close();
    }

    @Test void worldSwitchDiscardsPendingAndReadyImages() throws Exception {
        var a = new AtlasTileArchive(directory.resolve("a"));
        var b = new AtlasTileArchive(directory.resolve("b"));
        a.write(tile(0, 0), false);
        var empty = AtlasFixtures.world(Map.of(), List.of(), Set.of());
        var jobs = new ArrayDeque<Runnable>();
        try (var cache = new AtlasMinimapCache(jobs::add)) {
            cache.update(AtlasFixtures.archive(empty, a), 0, 0, 256);
            assertNull(cache.update(AtlasFixtures.archive(empty, b), 0, 0, 256));
            jobs.remove().run();
            assertNull(cache.update(AtlasFixtures.archive(empty, b), 0, 0, 256));
            jobs.remove().run();
            var frame = cache.update(AtlasFixtures.archive(empty, b), 0, 0, 256);
            assertArrayEquals(new DebugMapRaster(frame.window().size(), frame.window().size()).render(empty, frame.window().view()), frame.pixels());
        }
        a.close(); b.close();
    }

    @Test void unchangedPixelsDoNotUploadAgainWhenOnlySnapshotRevisionsChange() {
        var cells = List.of(tile(0, 0));
        var first = AtlasFixtures.world(Map.of(), cells, Set.of());
        var newer = AtlasFixtures.world(Map.of(), cells, Set.of(), List.of(), new DebugSnapshot.MapLayers(2, 2, 2, 2));
        try (var cache = new AtlasMinimapCache(Runnable::run)) {
            assertSame(prepare(cache, first, 0, 0), prepare(cache, newer, 0, 0));
        }
    }

    @Test void nearbyCaptureIsDeduplicatedAndReorderedAfterTeleport() {
        var queue = new AtlasTerrainQueue();
        long near = ChunkPos.pack(-2, -3), far = ChunkPos.pack(40, 50);
        queue.add(far); queue.add(near); queue.add(near);
        queue.focus(-2, -3);
        assertEquals(near, queue.poll());
        queue.add(near); queue.focus(40, 50);
        assertEquals(far, queue.poll());
        queue.remove(near);
        assertNull(queue.poll());
    }
}
