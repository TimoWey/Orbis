package dev.orbis.atlas;

import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AtlasTileArchiveTest {
    @TempDir Path directory;
    private static AtlasSnapshot.Cell tile(int x, int z, int color) {
        int[] pixels = new int[256];
        for (int i = 0; i < 256; i++) pixels[i] = color + i;
        return new AtlasSnapshot.Cell(x, z, color, pixels);
    }

    @Test void warmRegionsSurviveViewChangesAndResidentReadersNeverLoadColdFiles() throws Exception {
        var archive = new AtlasTileArchive(directory);
        archive.write(tile(0, 0, 0xff123400), false);
        archive.write(tile(31, 31, 0xff567800), false);
        try (var cold = archive.residentReader()) { assertNull(cold.get(0, 0)); }
        try (var reader = archive.reader()) { assertNotNull(reader.get(0, 0)); }
        // One chunk request warms the complete region for another screen without another disk read.
        java.nio.file.Files.delete(directory.resolve("r.0.0.tiles"));
        try (var reader = archive.reader(); var resident = archive.residentReader()) {
            assertEquals(0xff567800, reader.get(31, 31).terrainPixel(0));
            assertEquals(0xff567800, resident.get(31, 31).terrainPixel(0));
        }
    }

    @Test void completedWritesRefreshWarmRegionsWhileAnActiveProjectionStaysConsistent() throws Exception {
        var archive = new AtlasTileArchive(directory);
        archive.write(tile(0, 0, 0xff123400), false);
        try (var active = archive.reader()) {
            assertEquals(0xff123400, active.get(0, 0).terrainPixel(0));
            archive.write(tile(0, 0, 0xff567800), 3);
            try (var next = archive.residentReader()) {
                assertEquals(0xff567800, next.get(0, 0).terrainPixel(0));
            }
            assertEquals(0xff123400, active.get(0, 0).terrainPixel(0));
        }
        archive.close();
        try (var reader = archive.residentReader()) { assertNull(reader.get(0, 0)); }
    }

    @Test void originalPixelsSurviveRestartAndNegativeRegionBoundaries() throws Exception {
        var archive = new AtlasTileArchive(directory);
        for (int coordinate : new int[] {-33, -32, -1, 0, 31, 32}) archive.write(tile(coordinate, coordinate, 0xff123400), false);
        archive.close();
        try (var reader = new AtlasTileArchive(directory).reader()) {
            for (int coordinate : new int[] {-33, -32, -1, 0, 31, 32})
                assertArrayEquals(tile(coordinate, coordinate, 0xff123400).copyTerrain(), reader.get(coordinate, coordinate).copyTerrain());
            assertNull(reader.get(1, 1));
        }
    }

    @Test void dhCannotOverwriteNativeTerrainAndSavedDataCannotOverwriteLiveEdits() throws Exception {
        var archive = new AtlasTileArchive(directory);
        archive.write(tile(0, 0, 0xff000100), true);
        archive.write(tile(0, 0, 0xff000200), false);
        archive.write(tile(0, 0, 0xff000300), true);
        try (var reader = archive.reader()) { assertEquals(0xff000200, reader.get(0, 0).terrainPixel(0)); }
        archive.write(tile(0, 0, 0xff000400), 3);
        archive.write(tile(0, 0, 0xff000500), false);
        long revision = archive.revision();
        archive.write(tile(0, 0, 0xff000400), 3);
        assertEquals(revision, archive.revision());
        try (var reader = archive.reader()) { assertEquals(0xff000400, reader.get(0, 0).terrainPixel(0)); }
    }

    @Test void batchWritesSpanRegionsKeepRanksAndReportThem() throws Exception {
        var archive = new AtlasTileArchive(directory);
        archive.write(tile(1, 0, 0xff000900), 3);
        var batch = new ArrayList<AtlasSnapshot.Cell>();
        for (int x = -40; x < 40; x++) batch.add(tile(x, 0, 0xff000000 | (x & 255) << 8));
        long revision = archive.revision();
        archive.write(batch, 2);
        assertTrue(archive.revision() > revision);
        try (var reader = archive.reader()) {
            for (int x = -40; x < 40; x++) if (x != 1)
                assertEquals(0xff000000 | (x & 255) << 8, reader.get(x, 0).terrainPixel(0));
            // A saved tile never replaces a live capture, in a batch as alone.
            assertEquals(0xff000900, reader.get(1, 0).terrainPixel(0));
        }
        byte[] ranks = archive.ranks(0, 0);
        assertEquals(2, ranks[0]); assertEquals(3, ranks[1]); assertEquals(0, ranks[32]);
        assertEquals(2, archive.ranks(-2, 0)[24]);
        assertEquals(0, archive.ranks(5, 5)[0]);
        revision = archive.revision();
        archive.write(batch, 2);
        assertEquals(revision, archive.revision());
    }

    @Test void archiveRetainsMoreThanTheMemoryHistoryLimit() throws Exception {
        var archive = new AtlasTileArchive(directory);
        for (int x = 0; x <= AtlasCache.CHUNK_LIMIT; x++) archive.write(tile(x, 0, 0xff000000 | x << 8), false);
        try (var reader = archive.reader()) {
            assertEquals(0xff000000, reader.get(0, 0).terrainPixel(0));
            assertNotNull(reader.get(AtlasCache.CHUNK_LIMIT, 0));
        }
    }

    @Test void archiveProjectionMatchesLiveTerrainInBothViews() throws Exception {
        var archive = new AtlasTileArchive(directory);
        var cells = List.of(tile(-1, -1, 0xff102000), tile(0, -1, 0xff304000),
                tile(-1, 0, 0xff506000), tile(0, 0, 0xff708000));
        for (var cell : cells) archive.write(cell, false);
        var empty = AtlasFixtures.world(Map.of(), List.of(), Set.of());
        var live = AtlasFixtures.world(Map.of(), cells, Set.of());
        for (boolean globe : new boolean[] {false, true}) for (double scale : new double[] {.5, 1, 2}) {
            var view = new AtlasMapRaster.View(64, 64, 0, 0, scale, globe, 0, 0, false, false);
            var layer = AtlasTerrainBackdrop.render(archive, empty, view, 128, 128, () -> false);
            assertArrayEquals(new AtlasMapRaster(128, 128).render(live, view),
                    new AtlasMapRaster(128, 128).render(empty, view, layer));
        }
        assertNull(AtlasTerrainBackdrop.render(archive, empty,
                new AtlasMapRaster.View(64, 64, 0, 0, 1, false, 0, 0, false, false), 64, 64, () -> true));
    }

    @Test void corruptOrIncompleteSlotsDoNotInventTerrainAndSessionsStaySeparate() throws Exception {
        var archive = new AtlasTileArchive(directory.resolve("world-a"));
        archive.write(tile(0, 0, 0xff123400), false);
        try (var reader = new AtlasTileArchive(directory.resolve("world-b")).reader()) { assertNull(reader.get(0, 0)); }
        try (var file = new RandomAccessFile(directory.resolve("world-a/r.0.0.tiles").toFile(), "rw")) {
            file.seek(20); file.writeInt(123); // Complete slot, but invalid CRC.
        }
        try (var reader = archive.reader()) { assertNull(reader.get(0, 0)); }
        try (var file = new RandomAccessFile(directory.resolve("world-a/r.0.0.tiles").toFile(), "rw")) { file.setLength(100); }
        try (var reader = archive.reader()) { assertNull(reader.get(0, 0)); }
        archive.close();
        archive.write(tile(1, 0, 0xff123400), false);
        try (var reader = new AtlasTileArchive(directory.resolve("world-a")).reader()) { assertNull(reader.get(1, 0)); }
    }

    @Test void liveTerrainWinsOverArchiveButArchiveReplacesStaleMemoryHistory() throws Exception {
        var archive = new AtlasTileArchive(directory);
        archive.write(tile(0, 0, 0xff100000), false);
        var empty = AtlasFixtures.world(Map.of(), List.of(), Set.of());
        var old = tile(0, 0, 0xff200000);
        for (boolean globe : new boolean[] {false, true}) {
            var view = new AtlasMapRaster.View(64, 64, 8, 8, 1, globe, 0, 0, false, false);
            int[] layer = AtlasTerrainBackdrop.render(archive, empty, view, 64, 64, () -> false);
            var stale = AtlasFixtures.world(Map.of(), List.of(old), Set.of());
            var live = AtlasFixtures.world(Map.of(), List.of(old), Set.of(net.minecraft.world.level.ChunkPos.pack(0, 0)));
            int[] stalePixels = new AtlasMapRaster(64, 64).render(stale, view, layer);
            int[] livePixels = new AtlasMapRaster(64, 64).render(live, view, layer);
            assertEquals(layer[32 * 64 + 32], stalePixels[32 * 64 + 32]);
            assertNotEquals(stalePixels[32 * 64 + 32], livePixels[32 * 64 + 32]);
        }
    }

    @Test void regionalRevisionOnlyFollowsWritesInsideTheRectangle() throws Exception {
        var archive = new AtlasTileArchive(directory);
        long before = archive.revision(0, 0, 511, 511);
        archive.write(tile(40, 0, 0xff123400), false); // region (1, 0)
        assertEquals(before, archive.revision(0, 0, 511, 511));
        assertNotEquals(before, archive.revision(0, 0, 512, 511));
        assertEquals(archive.revision(), archive.revision(-100, -100, 600, 600));
    }

    @Test void localTilesDirectoryDependsOnlyOnShapeAndAdoptsTheNewestLegacyFingerprint() throws Exception {
        Path cache = directory.resolve("world").resolve("dimension.atlas.gz");
        Path stable = AtlasTileArchive.localDirectory(cache, -8192, -8192, 16384);
        assertEquals(stable, AtlasTileArchive.localDirectory(cache, -8192, -8192, 16384));
        assertNotEquals(stable, AtlasTileArchive.localDirectory(cache, -1024, -1024, 2048));
        assertEquals(cache.resolveSibling("dimension.atlas.gz.tiles"), stable.getParent());

        // Fingerprints of two older mod lists; the newer one holds the latest exploration.
        Path older = stable.resolveSibling(AtlasCache.hash("mods=a")), newer = stable.resolveSibling(AtlasCache.hash("mods=b"));
        new AtlasTileArchive(older).write(tile(0, 0, 0xff111100), false);
        new AtlasTileArchive(newer).write(tile(1, 0, 0xff222200), false);
        java.nio.file.Files.setLastModifiedTime(older.resolve("r.0.0.tiles"), java.nio.file.attribute.FileTime.fromMillis(1_000));
        java.nio.file.Files.setLastModifiedTime(older, java.nio.file.attribute.FileTime.fromMillis(1_000));
        Path otherShape = AtlasTileArchive.localDirectory(cache, -1024, -1024, 2048);
        new AtlasTileArchive(otherShape).write(tile(2, 0, 0xff333300), false);

        AtlasTileArchive.migrateLocal(stable);
        assertFalse(java.nio.file.Files.exists(older));
        assertFalse(java.nio.file.Files.exists(newer));
        assertTrue(java.nio.file.Files.exists(otherShape));
        try (var reader = new AtlasTileArchive(stable).reader()) {
            assertEquals(0xff222200, reader.get(1, 0).terrainPixel(0));
            assertNull(reader.get(0, 0));
        }

        // Once adopted, later fingerprint directories are only cleaned up, never merged over it.
        Path stale = stable.resolveSibling(AtlasCache.hash("mods=c"));
        new AtlasTileArchive(stale).write(tile(3, 0, 0xff444400), false);
        AtlasTileArchive.migrateLocal(stable);
        assertFalse(java.nio.file.Files.exists(stale));
        try (var reader = new AtlasTileArchive(stable).reader()) { assertNull(reader.get(3, 0)); }
    }
}
