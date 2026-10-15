package dev.orbis.engine.dh;

import static org.junit.jupiter.api.Assertions.*;

import com.seibel.distanthorizons.core.pos.DhSectionPos;
import dev.orbis.engine.Torus;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Section arithmetic of Distant Horizons on a torus (runs with the DH jar, in testDistantTerrain). */
final class DhTorusTest {
    private static final Torus DEBUG = Torus.centered(2048), TINY = Torus.centered(16384);

    private static long section(int detail, int minX, int minZ) {
        int width = 1 << detail;
        return DhSectionPos.encode((byte) detail, Math.floorDiv(minX, width), Math.floorDiv(minZ, width));
    }

    @Test void foldsSectionsBeyondTheSeamOntoTheirImage() {
        long north = section(6, -64, -1024 - 64);
        long home = DhTorus.fold(DEBUG, north);
        assertEquals(-64, DhSectionPos.getMinCornerBlockX(home));
        assertEquals(1024 - 64, DhSectionPos.getMinCornerBlockZ(home));
        long laps = section(8, 3 * 2048 + 256, -5 * 2048 + 512);
        assertEquals(section(8, 256, 512), DhTorus.fold(DEBUG, laps));
        long inside = section(10, 0, -1024);
        assertEquals(inside, DhTorus.home(DEBUG, inside));
    }

    @Test void onlyAlignedSectionsMove() {
        assertTrue(DhTorus.relocatable(DEBUG, section(10, -1024, 0)));
        // 2048 wide but aligned to multiples of 2048: it holds halves of two images of the world.
        assertFalse(DhTorus.relocatable(DEBUG, section(11, 0, 0)));
        assertTrue(DhTorus.translatable(DEBUG, section(11, 0, 0)));
        assertFalse(DhTorus.translatable(DEBUG, section(12, 0, 0)));
        assertTrue(DhTorus.relocatable(TINY, section(12, 4096, -8192)));
        long wide = section(11, 0, 0);
        assertEquals(wide, DhTorus.home(DEBUG, wide));
    }

    @Test void translatesByWholeWidths() {
        long pos = section(7, 128, -256);
        long moved = DhTorus.translate(pos, 2048, -4096);
        assertEquals(128 + 2048, DhSectionPos.getMinCornerBlockX(moved));
        assertEquals(-256 - 4096, DhSectionPos.getMinCornerBlockZ(moved));
        assertEquals(7, DhSectionPos.getDetailLevel(moved));
    }

    @Test void missingPartsAreAskedForAtHomeAndListedBesideThePlayer() {
        List<Long> asked = new ArrayList<>();
        long lap = section(8, 2048, 256);
        var result = DhTorus.missing(DEBUG, lap, (byte) 6, (pos, detail) -> {
            asked.add(pos);
            var list = new LongArrayList();
            list.add(DhSectionPos.getChildByIndex(DhSectionPos.getChildByIndex(pos, 0), 0));
            return list;
        });
        assertEquals(List.of(section(8, 0, 256)), asked);
        assertEquals(1, result.size());
        assertEquals(2048, DhSectionPos.getMinCornerBlockX(result.getLong(0)));
    }

    @Test void partsOfAWideSectionBeyondTheSeamAreMissingOnlyIfTheirImagesAre() {
        long wide = section(11, 0, 0); // [0, 2048): its east half lies past the seam
        long inside = section(6, 0, 0), outside = section(6, 1024, 0), outsideHome = section(6, -1024, 0);
        var result = DhTorus.missing(DEBUG, wide, (byte) 6, (pos, detail) -> {
            var list = new LongArrayList();
            if (pos == wide) { list.add(inside); list.add(outside); }
            // The image of the outside part is already generated.
            else if (pos != outsideHome) list.add(pos);
            return list;
        });
        assertEquals(LongArrayList.of(inside), result);
    }
}
