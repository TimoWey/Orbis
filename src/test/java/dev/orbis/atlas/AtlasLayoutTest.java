package dev.orbis.atlas;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasLayoutTest {
    @Test void mapAndControlsFitScaledWindowsWithoutOverlappingSidebar() {
        for (int[] window : new int[][]{{640,480}, {854,480}, {1280,720}, {1920,1080}, {3840,2160}}) {
            for (int scale = 1; scale <= 8; scale++) {
                int w = (window[0] + scale - 1) / scale, h = (window[1] + scale - 1) / scale;
                if (w < 320 || h < 240) continue; // Minecraft's GUI scale floor
                var layout = AtlasLayout.fit(w, h);
                assertTrue(layout.mapRight() - layout.mapLeft() >= 86);
                assertEquals(w / 2.0, (layout.mapLeft() + layout.mapRight()) / 2.0, .5);
                assertEquals(h / 2.0, (layout.mapTop() + layout.mapBottom()) / 2.0, .5);
                assertTrue(layout.mapBottom() - layout.mapTop() >= 48);
                assertTrue(layout.controlWidth() >= 32);
                assertTrue(layout.top() >= 12 && layout.bottom() <= h - 12);
                for (int i = 0; i < 4; i++) {
                    assertTrue(layout.controlX(i) >= layout.left());
                    assertTrue(layout.controlX(i) + layout.controlWidth() < layout.mapLeft());
                    assertTrue(layout.controlTop(i) >= layout.top());
                    assertTrue(layout.controlBottom(i) + 18 < layout.bottom());
                }
                assertTrue(layout.top() + 116 + 16 <= layout.bottom() - 28); // at least one checklist row
            }
        }
    }
    @Test void minimapCompassAndThreeCoordinateLinesFitEveryCorner() {
        for (int w : new int[]{320, 427, 640, 960, 1920}) for (int h : new int[]{240, 360, 540, 1080}) {
            for (int desired : new int[]{72, 96, 128}) {
                int size = OrbisMinimap.fittedSize(w, h, desired);
                assertTrue(size <= desired);
                assertTrue(size >= 72);
                for (int corner = 0; corner < 4; corner++) {
                    int x = corner % 2 == 0 ? w - size - 16 : 16;
                    int y = corner < 2 ? 16 : h - size - 78;
                    assertTrue(x - 12 >= 0 && x + size + 12 <= w);
                    assertTrue(y - 11 >= 0 && y + size + 45 <= h);
                }
            }
        }
    }
}
