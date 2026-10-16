package dev.orbis.atlas;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static dev.orbis.atlas.AtlasShading.*;
import static org.junit.jupiter.api.Assertions.*;

class AtlasShadingTest {
    @Test void landFollowsVanillaHeightStepToTheNorth() {
        for (int x = 0; x < 2; x++) {
            assertEquals(NORMAL, landBrightness(64, 64, x, 0));
            assertEquals(HIGH, landBrightness(65, 64, x, 0));
            assertEquals(LOW, landBrightness(63, 64, x, 0));
            assertEquals(HIGH, landBrightness(80, 64, x, 0));
        }
    }

    @Test void waterDepthUsesVanillaThresholdsAndCheckerboard() {
        assertEquals(HIGH, waterBrightness(1, 0, 0));
        assertEquals(HIGH, waterBrightness(3, 0, 0));
        assertEquals(NORMAL, waterBrightness(3, 1, 0)); // Dithered band between shallow and medium.
        assertEquals(NORMAL, waterBrightness(6, 0, 0));
        assertEquals(LOW, waterBrightness(8, 1, 0));
        assertEquals(LOW, waterBrightness(WATER_DEPTH_LIMIT, 0, 0));
        assertEquals(LOW, waterBrightness(WATER_DEPTH_LIMIT, 1, 0));
    }

    @Test void normalBrightnessKeepsTheRawColourAndOthersStayOpaque() {
        assertEquals(0xff707070, shade(0xff707070, NORMAL));
        assertTrue((shade(0xff707070, LOW) & 255) < 0x70);
        assertTrue((shade(0xff707070, HIGH) & 255) > 0x70);
        assertEquals(0xffffffff, shade(0xffffffff, HIGH));
        assertEquals(0xff000000, shade(0xff707070, LOW) & 0xff000000);
    }

    @Test void tileUsesInTileNeighboursAndSkipsFlatColumns() {
        int[] colors = new int[256], heights = new int[256], depths = new int[256];
        Arrays.fill(colors, 0xff808080);
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) heights[z * 16 + x] = 64 + z; // Rising southwards.
        heights[5 * 16 + 3] = FLAT;
        depths[9 * 16 + 9] = 2;
        shadeTile(colors, heights, depths, -16, 32);
        int high = shade(0xff808080, HIGH);
        assertEquals(high, colors[0]); // Row 0 extrapolates the step to row 1, no seam at the chunk edge.
        assertEquals(high, colors[3 * 16 + 7]);
        assertEquals(0xff808080, colors[5 * 16 + 3]);
        assertEquals(0xff808080, colors[6 * 16 + 3]); // North neighbour without height: no invented relief.
        assertEquals(high, colors[9 * 16 + 9]); // Shallow water by depth, not slope.

        Arrays.fill(colors, 0xff808080); Arrays.fill(heights, 70); Arrays.fill(depths, 0);
        shadeTile(colors, heights, depths, 0, 0);
        for (int color : colors) assertEquals(0xff808080, color); // Flat ground matches unshaded archives.
    }
}
