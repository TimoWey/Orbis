package dev.orbis.debug;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasSearchTest {
    private static final AtlasBiomeSampler.Style PLAINS = new AtlasBiomeSampler.Style("plains", 0xff336633);
    private AtlasSearch search(int span, int[] heights) {
        return new AtlasSearch(-span / 2, -span / 2, span, -64, 319, heights, true, () -> 0);
    }
    @Test void quickQueryCountIsSmallAndIndependentOfWorldSize() {
        for (int span : new int[]{16384, 1224704, 10000128, 23170560}) {
            var search = search(span, new int[]{63, -48, 32, 128});
            var calls = new AtomicInteger();
            search.search(1, () -> true, () -> false, (x,y,z) -> {
                assertTrue(x >= -span / 2 && x < span / 2);
                assertTrue(z >= -span / 2 && z < span / 2);
                calls.incrementAndGet(); return PLAINS;
            }, (x,y,z,id) -> {});
            assertEquals(1024, calls.get()); assertFalse(search.save().running());
            search.search(1, () -> true, () -> false, (x,y,z) -> fail("Budget must stay stopped"), (x,y,z,id) -> {});
        }
    }
    @Test void netherDuplicateHeightsAreNotQueriedTwice() {
        var search = search(2048, new int[]{32, 16, 32, 128});
        var positions = new HashSet<List<Integer>>();
        search.search(1, () -> true, () -> false, (x,y,z) -> {
            assertTrue(positions.add(List.of(x,y,z))); return PLAINS;
        }, (x,y,z,id) -> {});
        assertEquals(768, search.save().cursor()); assertEquals(768, positions.size());
    }
    @Test void stopsAtFirstCompleteChecklistAndMapKeepsIndependentProgress() {
        var search = search(16384, new int[]{63, -48, 32, 128});
        var found = new HashSet<String>();
        search.search(1, () -> true, () -> found.contains("plains"), (x,y,z) -> PLAINS, (x,y,z,id) -> found.add(id));
        assertEquals(1, search.save().cursor()); assertFalse(search.save().running());
        var pixels = new AtomicInteger();
        search.map(1, () -> true, (x,y,z) -> PLAINS, (x,y,z,id) -> {}, (x,z,grid,color) -> {
            assertEquals(16, grid); pixels.incrementAndGet();
        });
        assertEquals(256, pixels.get()); assertFalse(search.save().mapping());
        assertEquals(1, search.save().cursor());
        search.toggleMap(); assertTrue(search.save().mapping());
        var mapCalls = new AtomicInteger();
        search.map(1, () -> mapCalls.get() < 17, (x,y,z) -> PLAINS, (x,y,z,id) -> {}, (x,z,grid,color) -> {
            assertEquals(256, grid); mapCalls.incrementAndGet();
        });
        assertEquals(273, search.save().mapCursor());
        search.resample(); assertEquals(273, search.save().mapCursor(), "Resample must retain map work");
    }
    @Test void pausedAndPartialSearchesRestoreTheExactNextPosition() {
        var original = search(16384, new int[]{63, -48, 32, 128});
        var first = new ArrayList<List<Integer>>();
        AtlasSearch.Query query = (x,y,z) -> { first.add(List.of(x,y,z)); return PLAINS; };
        original.search(1, () -> true, () -> false, query, (x,y,z,id) -> {});
        original.toggle(false);
        original.search(1, () -> first.size() < 1733, () -> false, query, (x,y,z,id) -> {});
        var saved = original.save();
        var restored = search(16384, new int[]{63, -48, 32, 128}); restored.restore(saved);
        var tailA = new ArrayList<List<Integer>>(); var tailB = new ArrayList<List<Integer>>();
        original.search(1, () -> true, () -> false, (x,y,z) -> { tailA.add(List.of(x,y,z)); return PLAINS; }, (x,y,z,id) -> {});
        restored.search(1, () -> true, () -> false, (x,y,z) -> { tailB.add(List.of(x,y,z)); return PLAINS; }, (x,y,z,id) -> {});
        assertEquals(tailA, tailB); assertEquals(1024 + 4096, restored.save().cursor());
        restored.toggle(false); restored.pause();
        original.restore(restored.save()); assertFalse(original.save().running());
    }
    @Test void unsuccessfulDeepeningHasAHardCapAndResampleIsExplicit() {
        var search = search(16384, new int[]{63, -48, 32, 128});
        for (int pass = 0; pass < 12; pass++) {
            search.search(1, () -> true, () -> false, (x,y,z) -> {
                assertTrue(y >= -64 && y <= 319); return PLAINS;
            }, (x,y,z,id) -> {});
            int before = search.save().cursor(); search.toggle(false);
            assertTrue(search.save().limit() - before <= 4096);
        }
        assertEquals(AtlasSearch.MAX, search.save().cursor()); assertFalse(search.save().running());
        search.resample(); assertEquals(1024, search.save().limit()); assertEquals(0, search.save().cursor());
    }
    @Test void deadlineCancellationAndAlreadyObservedChecklistDoNoExtraWork() {
        var search = search(16384, new int[]{63, -48, 32, 128});
        AtlasSearch.Query forbidden = (x,y,z) -> fail("No query authorized");
        search.search(0, () -> true, () -> false, forbidden, (x,y,z,id) -> {});
        search.search(1, () -> false, () -> false, forbidden, (x,y,z,id) -> {});
        search.search(1, () -> true, () -> true, forbidden, (x,y,z,id) -> {});
        assertEquals(0, search.save().cursor()); assertFalse(search.save().running());
        search.map(0, () -> true, forbidden, (x,y,z,id) -> {}, (x,z,grid,color) -> {});
        assertEquals(0, search.save().mapCursor());
    }
    @Test void invalidCheckpointsCannotAuthorizeUnboundedWork() {
        assertThrows(IllegalArgumentException.class, () -> new AtlasSearch.Saved(5, 4, true, 0, 256, true));
        assertThrows(IllegalArgumentException.class, () -> new AtlasSearch.Saved(0, AtlasSearch.MAX + 1, true, 0, 256, true));
        assertThrows(IllegalArgumentException.class, () -> new AtlasSearch.Saved(0, 1024, true, 0, Integer.MAX_VALUE, true));
    }

    @Test void extraHeightsExploreEveryVerticalBandForOneToFourFixedHeights() {
        for (int[] heights : new int[][] {{63}, {63, -48}, {63, -48, 32}, {63, -48, 32, 128}}) {
            var search = search(16384, heights);
            search.search(1, () -> true, () -> false, (x,y,z) -> PLAINS, (x,y,z,id) -> {});
            search.toggle(false);
            var bands = new HashSet<Integer>();
            var query = new AtomicInteger();
            search.search(1, () -> true, () -> false, (x,y,z) -> {
                if (query.incrementAndGet() % (heights.length + 1) == 0) bands.add((y + 64) / 48);
                return PLAINS;
            }, (x,y,z,id) -> {});
            assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7), bands,
                    "Variable-height queries must cover the full dimension height");
        }
    }

    @Test void slowQueryStopsBeforeStartingAnotherAfterDeadline() {
        var clock = new AtomicInteger();
        var search = new AtlasSearch(-1024, -1024, 2048, -64, 319,
                new int[]{63, -48, 32, 128}, true, clock::get);
        var queries = new AtomicInteger();
        search.search(3, () -> true, () -> false, (x,y,z) -> {
            queries.incrementAndGet(); clock.addAndGet(5); return PLAINS;
        }, (x,y,z,id) -> {});
        assertEquals(1, queries.get());
        assertEquals(1, search.save().cursor());
    }
}
