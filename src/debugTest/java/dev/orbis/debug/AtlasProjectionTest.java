package dev.orbis.debug;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasProjectionTest {
    @Test void localScaleIsEqualOnBothAxesEvenAtOldPolesAndAcrossSeams() {
        for (double u : new double[]{0, .00001, .3, .99999}) for (double v : new double[]{0, .00001, .5, .99999}) {
            double yaw = -((u - .5) * Math.PI * 2), pitch = (.5 - v) * Math.PI;
            assertArrayEquals(new double[]{0, 0, 1}, DebugMapMath.globe(u, v, yaw, pitch), 1e-10);
            var east = DebugMapMath.globe(u + .001, v, yaw, pitch);
            var south = DebugMapMath.globe(u, v + .001, yaw, pitch);
            assertEquals(east[0], -south[1], 1e-10);
            assertEquals(east[2], south[2], 1e-10);
            assertArrayEquals(east, DebugMapMath.globe(u + 1.001, v - 1, yaw, pitch), 1e-10);
            double[] uv = DebugMapMath.unproject(east[0], east[1], yaw, pitch);
            assertEquals(DebugMapMath.unit(u + .001), uv[0], 1e-10);
            assertEquals(DebugMapMath.unit(v), uv[1], 1e-10);
        }
    }
    @Test void rasterAndInteractiveInverseUseIdenticalMovingOrigin() {
        Map<Integer, Integer> colors = new HashMap<>();
        for (int i = 0; i < 65536; i++) colors.put(i, 0xff000000 | i);
        var data = world(colors, List.of());
        var raster = new DebugMapRaster(128, 128);
        for (double yaw : new double[]{-9.1, 0, 2.8}) for (double pitch : new double[]{-4, .7, 8}) {
            var view = new DebugMapRaster.View(128, 128, 0, 0, 1, true, yaw, pitch, false, false);
            int[] pixels = raster.render(data, view);
            assertEquals(0, pixels[0]);
            for (int y = 8; y < 120; y += 7) for (int x = 8; x < 120; x += 7) {
                double[] uv = DebugMapMath.unproject((x + .5 - 64) / 60, (64 - y - .5) / 60, yaw, pitch);
                if (uv == null) { assertEquals(0, pixels[y * 128 + x]); continue; }
                int sample = (int) (uv[1] * 256) * 256 + (int) (uv[0] * 256);
                assertEquals(colors.get(sample), pixels[y * 128 + x]);
                var marker = DebugMapMath.globe(uv[0], uv[1], yaw, pitch);
                assertEquals(x + .5, 64 + marker[0] * 60, 1e-8);
                assertEquals(y + .5, 64 - marker[1] * 60, 1e-8);
            }
        }
    }
    @Test void flatOverviewContainsOneWorldAndClampedPanCannotEscapeIt() {
        Map<Integer, Integer> colors = new HashMap<>();
        for (int i = 0; i < 65536; i++) colors.put(i, 0xff456789);
        int[] pixels = new DebugMapRaster(192, 128).render(world(colors, List.of()),
                new DebugMapRaster.View(192, 128, 0, 0, 128.0 / 2048, false, 0, 0, false, false));
        for (int y = 0; y < 128; y++) for (int x = 0; x < 192; x++) {
            if (x < 32 || x >= 160) assertEquals(DebugMapRaster.BACKGROUND, pixels[y * 192 + x]);
        }
        assertEquals(0xff456789, pixels[64 * 192 + 96]);
        assertEquals(0, DebugMapMath.clampCenter(10000, -1024, 2048, 3000));
        assertEquals(896, DebugMapMath.clampCenter(10000, -1024, 2048, 256));
        assertEquals(-896, DebugMapMath.clampCenter(-10000, -1024, 2048, 256));
        assertEquals(-1024, DebugMapMath.canonical(1024, -1024, 2048));
    }
    @Test void exploredBlocksHaveIndividualColorsAndCoarseLevelsAverageThem() {
        List<Integer> colors = new ArrayList<>(Collections.nCopies(256, 0xff000000));
        colors.set(0, 0xffffffff); colors.set(1, 0xffff0000);
        var cell = new DebugSnapshot.Cell(-1, -1, 0xff00ff00, colors);
        assertEquals(0xffffffff, cell.colorAt(-16, -16, 1));
        assertEquals(0xffff0000, cell.colorAt(-15, -16, 1));
        assertEquals(0xff1f0f0f, cell.colorAt(-16, -16, 4));
        int[] pixels = new DebugMapRaster(64, 64).render(world(Map.of(), List.of(cell)),
                new DebugMapRaster.View(64, 64, -8, -8, 2, false, 0, 0, false, false));
        assertEquals(0xffffffff, pixels[16 * 64 + 16]);
        assertEquals(0xffff0000, pixels[16 * 64 + 18]);
        assertEquals(DebugMapRaster.BACKGROUND, pixels[10 * 64 + 10]);
    }
    private DebugSnapshot world(Map<Integer, Integer> colors, List<DebugSnapshot.Cell> chunks) {
        return new DebugSnapshot("minecraft:overworld", 1, "Custom", 1, -1024, -1024, 2048,
                true, 0, 64, 0, 0, chunks, Set.of(), List.of(), List.of(), colors, 0,
                DebugSurvey.TOTAL, false, false, "", List.of(), false, 0, "");
    }
}
