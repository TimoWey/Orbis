package dev.orbis.atlas;

import dev.orbis.WorldSize;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureAuditTraversalTest {
    @Test void distributedTraversalVisitsEveryRegionExactlyOnceIncludingUnequalEdges() {
        for (int width : new int[] {1, 2, 3, 7, 16, 37, 128}) {
            for (int spacing : new int[] {1, 3, 8, 32}) {
                var grid = new StructureAudit.Grid(-79, -35, width, spacing);
                var expected = new HashSet<ChunkPos>();
                for (int z = grid.startZ; z < grid.startZ + grid.rows; z++)
                    for (int x = grid.startX; x < grid.startX + grid.columns; x++)
                        expected.add(new ChunkPos(x * spacing, z * spacing));
                var visited = new HashSet<ChunkPos>();
                for (long i = 0; i < grid.total; i++) assertTrue(visited.add(grid.at(i)), "Duplicate region");
                assertEquals(expected, visited);
            }
        }
    }

    @Test void firstProbesCoverBothAxesAndResumeWithoutChangingTheirOrder() {
        for (int width : new int[] {WorldSize.DEFAULT.overworldChunks(), WorldSize.EARTH_CHUNKS}) {
            var grid = new StructureAudit.Grid(-width / 2, -width / 2, width, 32);
            var quadrants = new HashSet<Integer>();
            var visited = new HashSet<ChunkPos>();
            for (int i = 0; i < 256; i++) {
                var point = grid.at(i);
                assertTrue(visited.add(point));
                // Classify placement regions, whose origin can precede the world edge.
                int x = point.x() / 32 - grid.startX, z = point.z() / 32 - grid.startZ;
                if (i < 4) quadrants.add((x >= grid.columns / 2 ? 1 : 0) | (z >= grid.rows / 2 ? 2 : 0));
            }
            assertEquals(Set.of(0, 1, 2, 3), quadrants);
            var restored = new StructureAudit.Grid(-width / 2, -width / 2, width, 32);
            for (long i = 113; i < 256; i++) assertEquals(grid.at(i), restored.at(i));
            assertThrows(IndexOutOfBoundsException.class, () -> grid.at(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> grid.at(grid.total));
        }
    }

    @Test void recentLayoutsAvoidDuplicateJobsAndRemainBoundedAndResettable() {
        var recent = new StructureAudit.RecentLayouts(2);
        recent.remember(10); recent.remember(20); recent.remember(10);
        assertTrue(recent.contains(10)); assertTrue(recent.contains(20));
        recent.remember(30);
        assertFalse(recent.contains(10));
        assertTrue(recent.contains(20)); assertTrue(recent.contains(30));
        recent.clear();
        assertFalse(recent.contains(20)); assertFalse(recent.contains(30));
    }
}
