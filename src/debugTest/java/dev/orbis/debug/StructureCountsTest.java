package dev.orbis.debug;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureCountsTest {
    @Test void chunkReloadsAndOverlappingPredictionsNeverDoubleCount() {
        var counts = new StructureCounts();
        long a = ChunkPos.pack(-12, 17), b = ChunkPos.pack(36, -5);
        assertTrue(counts.add("stronghold", a, false));
        assertFalse(counts.add("stronghold", a, false));
        assertTrue(counts.add("stronghold", a, true));
        assertFalse(counts.add("stronghold", a, true));
        assertTrue(counts.add("stronghold", b, false));
        assertEquals(new StructureCounts.Count(1, 2, 2), counts.count("stronghold"));
        assertTrue(counts.add(StructureAudit.PORTAL, a, false));
        assertEquals(1, counts.count(StructureAudit.PORTAL).distinct());
        assertEquals(2, counts.count("stronghold").distinct());
    }
    @Test void cacheResumePreservesIdentityAndResampleRetainsOnlyObservedOccurrences() {
        var original = new StructureCounts();
        original.add("village", 10, false); original.add("village", 10, true);
        original.add("village", 20, false);
        var restored = new StructureCounts(); restored.restore(original.save());
        assertFalse(restored.add("village", 20, false));
        assertTrue(restored.add("village", 30, false));
        assertEquals(new StructureCounts.Count(1, 3, 3), restored.count("village"));
        restored.clearPredictions();
        assertEquals(new StructureCounts.Count(1, 0, 1), restored.count("village"));
        assertTrue(restored.add("village", 10, false));
        assertEquals(new StructureCounts.Count(1, 1, 1), restored.count("village"));
    }
    @Test void incompleteAndStorageLimitedCountsRemainExplicitLowerBounds() {
        var place = new DebugSnapshot.Place("stronghold", 0, 32, 0);
        assertEquals("3+", new StructureAudit.Entry("stronghold", place, false, false, 0, 3, 3, false).countLabel());
        assertEquals("3", new StructureAudit.Entry("stronghold", place, true, true, 1, 3, 3, false).countLabel());
        assertEquals("3+", new StructureAudit.Entry("stronghold", place, true, true, 1, 3, 3, true).countLabel());
        var unknown = new StructureAudit.Entry("stronghold", null, false, false, 0, 0, 0, false);
        assertEquals("0+", unknown.countLabel()); assertFalse(unknown.found());
        var capped = new StructureCounts(2);
        capped.add("village", 1, true); capped.add("village", 2, false);
        assertFalse(capped.add("village", 3, false)); assertTrue(capped.limited());
        var restored = new StructureCounts(2); restored.restore(capped.save());
        assertTrue(restored.limited()); assertEquals(2, restored.count("village").distinct());
    }
}
