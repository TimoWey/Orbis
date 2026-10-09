package dev.orbis.debug;

import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AtlasTileArchiveTest {
    @TempDir Path directory;
    private static DebugSnapshot.Cell tile(int x, int z, int color) {
        int[] pixels = new int[256];
        for (int i = 0; i < 256; i++) pixels[i] = color + i;
        return new DebugSnapshot.Cell(x, z, color, pixels);
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
            var view = new DebugMapRaster.View(64, 64, 0, 0, scale, globe, 0, 0, false, false);
            var layer = AtlasTerrainBackdrop.render(archive, empty, view, 128, 128, () -> false);
            assertArrayEquals(new DebugMapRaster(128, 128).render(live, view),
                    new DebugMapRaster(128, 128).render(empty, view, layer));
        }
        assertNull(AtlasTerrainBackdrop.render(archive, empty,
                new DebugMapRaster.View(64, 64, 0, 0, 1, false, 0, 0, false, false), 64, 64, () -> true));
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
            var view = new DebugMapRaster.View(64, 64, 8, 8, 1, globe, 0, 0, false, false);
            int[] layer = AtlasTerrainBackdrop.render(archive, empty, view, 64, 64, () -> false);
            var stale = AtlasFixtures.world(Map.of(), List.of(old), Set.of());
            var live = AtlasFixtures.world(Map.of(), List.of(old), Set.of(net.minecraft.world.level.ChunkPos.pack(0, 0)));
            int[] stalePixels = new DebugMapRaster(64, 64).render(stale, view, layer);
            int[] livePixels = new DebugMapRaster(64, 64).render(live, view, layer);
            assertEquals(layer[32 * 64 + 32], stalePixels[32 * 64 + 32]);
            assertNotEquals(stalePixels[32 * 64 + 32], livePixels[32 * 64 + 32]);
        }
    }
}
