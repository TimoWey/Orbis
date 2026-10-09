package dev.orbis.atlas;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasTerrainCollectorTest {
    @Test void slowInitialCaptureFinishesDespitePeriodicRefresh() {
        var clock = new AtomicLong();
        var collector = new AtlasTerrainCollector(clock::get);
        for (int z = -16; z <= 16; z++) for (int x = -16; x <= 16; x++) collector.load(ChunkPos.pack(x,z));
        var seen = new HashSet<Long>();
        for (int tick = 0; tick < 1400; tick++) collector.tick(0, 0, key -> {
            seen.add(key); clock.addAndGet(2_000_001); return tile(key);
        }, t -> {});
        System.out.printf("SLOW_INITIAL_CAPTURE unique=%d/1089 after=1400 ticks; simulated cost=2.000001ms%n", seen.size());
        assertEquals(1089, seen.size(), "Refresh work must not starve uncaptured chunks");
    }
    @Test void temporarilyUnavailableChunkRetriesOnTheNextTick() {
        var collector = new AtlasTerrainCollector(() -> 0);
        collector.load(ChunkPos.pack(0,0));
        assertEquals(1, collector.tick(0,0,k -> null, t -> fail()));
        assertEquals(1, collector.tick(0,0,AtlasTerrainCollectorTest::tile,t -> {}));
        assertEquals(1, collector.cells().size());
    }
    @Test void editsStillRefreshDuringLongInitialCaptureAndLoadedSnapshotsStayImmutable() {
        var clock = new AtomicLong(); var collector = new AtlasTerrainCollector(clock::get);
        for (int x=0; x<1500; x++) collector.load(ChunkPos.pack(x,0));
        long center = ChunkPos.pack(0,0);
        var loaded = collector.loadedSnapshot(); long revision = collector.loadedRevision();
        assertSame(loaded,collector.loadedSnapshot());
        int[] centerCaptures = {0};
        for (int i=0; i<400; i++) collector.tick(0,0,key -> {
            if (key == center) centerCaptures[0]++;
            clock.addAndGet(2_000_001); return tile(key);
        }, t -> {});
        assertTrue(centerCaptures[0] >= 2, "Edits need a refresh budget during initial capture");
        collector.unload(center,AtlasTerrainCollectorTest::tile,t -> {});
        assertTrue(loaded.contains(center)); assertFalse(collector.loadedSnapshot().contains(center));
        assertTrue(collector.loadedRevision() > revision);
    }
    @Test void unchangedRefreshDoesNotQueueMoreDiskWritesButEditsDo() {
        var collector = new AtlasTerrainCollector(() -> 0); long key = ChunkPos.pack(0,0);
        collector.load(key); var writes = new ArrayList<AtlasSnapshot.Cell>();
        for (int tick=0; tick<400; tick++) collector.tick(0,0,AtlasTerrainCollectorTest::tile,writes::add);
        assertEquals(1,writes.size());
        for (int tick=0; tick<200; tick++) collector.tick(0,0,k -> new AtlasSnapshot.Cell(0,0,0xffff0000),writes::add);
        assertEquals(2,writes.size()); assertEquals(0xffff0000,writes.getLast().color());
    }
    private static AtlasSnapshot.Cell tile(long key) {
        return new AtlasSnapshot.Cell(ChunkPos.getX(key), ChunkPos.getZ(key), 0xff336699);
    }
    @Test void playerAndHudLoadFirstInsteadOfWaitingForTheCornerScan() {
        var clock = new AtomicLong();
        var collector = new AtlasTerrainCollector(clock::get);
        for (int z = -16; z <= 16; z++) for (int x = -16; x <= 16; x++) collector.load(ChunkPos.pack(x,z));
        var captured = new ArrayList<AtlasSnapshot.Cell>();
        int ticks = 0;
        while (captured.stream().filter(c -> Math.abs(c.x()) <= 4 && Math.abs(c.z()) <= 4).count() < 81) {
            collector.tick(0,0, AtlasTerrainCollectorTest::tile, captured::add); ticks++;
        }
        assertEquals(0, captured.getFirst().x()); assertEquals(0, captured.getFirst().z());
        // Model alpha.8's row scan: four coordinates per tick across the same 33x33 window.
        int oldPlayerTicks = (16 * 33 + 16) / 4 + 1;
        int oldHudTicks = (20 * 33 + 20) / 4 + 1;
        assertTrue(ticks <= 4); // 81 central chunks fit within four nearest-first batches.
        System.out.printf("TERRAIN_QUEUE alpha8Player=%d alpha8Hud=%d alpha9Player=1 alpha9Hud=%d ticks; budget=2ms maxBatch=32%n", oldPlayerTicks,oldHudTicks,ticks);
    }
    @Test void softBudgetStopsFurtherQueriesAndTeleportReordersPendingChunks() {
        var clock = new AtomicLong();
        var collector = new AtlasTerrainCollector(clock::get);
        collector.load(ChunkPos.pack(0,0));collector.load(ChunkPos.pack(1,0));collector.load(ChunkPos.pack(100,100));
        var seen = new ArrayList<Long>();
        assertEquals(1, collector.tick(0,0,key -> { seen.add(key); clock.addAndGet(2_000_001);return tile(key); }, t -> {}));
        assertEquals(ChunkPos.pack(0,0), seen.getFirst());
        assertEquals(1, collector.tick(100,100,key -> { seen.add(key);clock.addAndGet(2_000_001);return tile(key); },t -> {}));
        assertEquals(ChunkPos.pack(100,100),seen.getLast());
    }
    @Test void unloadCapturesPendingChunkAndUnchangedSnapshotsReuseTheSameList() {
        var collector = new AtlasTerrainCollector(() -> 0);
        long key=ChunkPos.pack(-7,9);collector.load(key);
        var captured = new ArrayList<AtlasSnapshot.Cell>();
        collector.unload(key,AtlasTerrainCollectorTest::tile,captured::add);
        assertEquals(1,captured.size());assertFalse(collector.loaded.contains(key));
        var before=collector.cells();long revision=collector.revision();
        collector.accept(tile(key));
        assertSame(before,collector.cells());assertEquals(revision,collector.revision());
        assertEquals(0,collector.tick(0,0,k -> {fail("Unloaded chunk must not be requested");return null;},t -> {}));
    }
}
