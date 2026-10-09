package dev.orbis.atlas;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasViewBoundsTest {
    @Test void allIncludesDistantMappedChunksAndPlayer() {
        var bounds = AtlasViewBounds.known(List.of(new AtlasSnapshot.Cell(-1000, 50, 0)),
                100000, 40000);
        assertTrue(bounds.centerX() - bounds.width() / 2 < -16000);
        assertTrue(bounds.centerX() + bounds.width() / 2 > 100000);
        assertTrue(bounds.centerZ() - bounds.depth() / 2 < 800);
        assertTrue(bounds.centerZ() + bounds.depth() / 2 > 40000);
        for (int[] view : new int[][]{{80,200},{500,100}}) {
            double zoom = bounds.zoom(4096, view[0], view[1]);
            assertTrue(zoom < 1, "Distant End exploration must fit beyond the old 4096 block view");
            double scale = Math.min(view[0], view[1]) * zoom / 4096;
            assertTrue(bounds.width() * scale <= view[0] + .000001);
            assertTrue(bounds.depth() * scale <= view[1] + .000001);
        }
    }
    @Test void emptyMapCentersOnPlayerWithUsableScale() {
        var bounds = AtlasViewBounds.known(List.of(), 12000, -9000);
        assertEquals(12000, bounds.centerX()); assertEquals(-9000, bounds.centerZ());
        assertEquals(128, bounds.width()); assertEquals(128, bounds.depth());
    }
    @Test void distantChunksDoNotOverflowIntegerCoordinates() {
        var bounds = AtlasViewBounds.known(List.of(new AtlasSnapshot.Cell(2000000, -2000000, 0)), 0, 0);
        assertTrue(bounds.width() > 32000000); assertTrue(bounds.depth() > 32000000);
    }
}
