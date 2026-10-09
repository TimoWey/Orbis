package dev.orbis.atlas;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasProjectionTest {
    @Test void localScaleIsEqualOnBothAxesEvenAtOldPolesAndAcrossSeams() {
        for (double u : new double[]{0, .00001, .3, .99999}) for (double v : new double[]{0, .00001, .5, .99999}) {
            double yaw = -((u - .5) * Math.PI * 2), pitch = (.5 - v) * Math.PI;
            assertArrayEquals(new double[]{0, 0, 1}, AtlasMapMath.globe(u, v, yaw, pitch), 1e-10);
            var east = AtlasMapMath.globe(u + .001, v, yaw, pitch);
            var south = AtlasMapMath.globe(u, v + .001, yaw, pitch);
            assertEquals(east[0], -south[1], 1e-10);
            assertEquals(east[2], south[2], 1e-10);
            assertArrayEquals(east, AtlasMapMath.globe(u + 1.001, v - 1, yaw, pitch), 1e-10);
            double[] uv = AtlasMapMath.unproject(east[0], east[1], yaw, pitch);
            assertEquals(AtlasMapMath.unit(u + .001), uv[0], 1e-10);
            assertEquals(AtlasMapMath.unit(v), uv[1], 1e-10);
        }
    }
    @Test void rasterAndInteractiveInverseUseIdenticalMovingOrigin() {
        var cells = new ArrayList<AtlasSnapshot.Cell>();
        for (int z = 0; z < 128; z++) for (int x = 0; x < 128; x++)
            cells.add(new AtlasSnapshot.Cell(x - 64, z - 64, 0xff000000 | z * 128 + x));
        var data = world(Map.of(), cells);
        var raster = new AtlasMapRaster(128, 128);
        for (double yaw : new double[]{-9.1, 0, 2.8}) for (double pitch : new double[]{-4, .7, 8}) {
            var view = new AtlasMapRaster.View(128, 128, 0, 0, 1, true, yaw, pitch, false, false);
            int[] pixels = raster.render(data, view);
            assertEquals(0, pixels[0]);
            for (int y = 8; y < 120; y += 7) for (int x = 8; x < 120; x += 7) {
                double[] uv = AtlasMapMath.unproject((x + .5 - 64) / 60, (64 - y - .5) / 60, yaw, pitch);
                if (uv == null) { assertEquals(0, pixels[y * 128 + x]); continue; }
                int sample = (int) (uv[1] * 128) * 128 + (int) (uv[0] * 128);
                assertEquals(0xff000000 | sample, pixels[y * 128 + x]);
                var marker = AtlasMapMath.globe(uv[0], uv[1], yaw, pitch);
                assertEquals(x + .5, 64 + marker[0] * 60, 1e-8);
                assertEquals(y + .5, 64 - marker[1] * 60, 1e-8);
            }
        }
    }
    @Test void flatOverviewContainsOneWorldAndClampedPanCannotEscapeIt() {
        Map<Integer, Integer> colors = new HashMap<>();
        for (int i = 0; i < 65536; i++) colors.put(i, 0xff456789);
        int[] pixels = new AtlasMapRaster(192, 128).render(world(colors, List.of()),
                new AtlasMapRaster.View(192, 128, 0, 0, 128.0 / 2048, false, 0, 0, false, false));
        for (int y = 0; y < 128; y++) for (int x = 0; x < 192; x++) {
            if (x < 32 || x >= 160) assertEquals(AtlasMapRaster.BACKGROUND, pixels[y * 192 + x]);
        }
        assertEquals(AtlasUnknownTerrain.color(16, 16, AtlasUnknownTerrain.level(32)), pixels[64 * 192 + 96]);
        assertEquals(0, AtlasMapMath.clampCenter(10000, -1024, 2048, 3000));
        assertEquals(896, AtlasMapMath.clampCenter(10000, -1024, 2048, 256));
        assertEquals(-896, AtlasMapMath.clampCenter(-10000, -1024, 2048, 256));
        assertEquals(-1024, AtlasMapMath.canonical(1024, -1024, 2048));
    }
    @Test void exploredBlocksHaveIndividualColors() {
        List<Integer> colors = new ArrayList<>(Collections.nCopies(256, 0xff000000));
        colors.set(0, 0xffffffff); colors.set(1, 0xffff0000);
        var cell = new AtlasSnapshot.Cell(-1, -1, 0xff00ff00, colors);
        assertEquals(0xffffffff, cell.colorAt(-16, -16));
        assertEquals(0xffff0000, cell.colorAt(-15, -16));
        int[] pixels = new AtlasMapRaster(64, 64).render(world(Map.of(), List.of(cell)),
                new AtlasMapRaster.View(64, 64, -8, -8, 2, false, 0, 0, false, false));
        assertEquals(0xffffffff, pixels[16 * 64 + 16]);
        assertEquals(0xffff0000, pixels[16 * 64 + 18]);
        assertEquals(AtlasUnknownTerrain.color(-37.5, -37.5, 0), pixels[10 * 64 + 10]);
    }
    private AtlasSnapshot world(Map<Integer, Integer> colors, List<AtlasSnapshot.Cell> chunks) {
        return AtlasFixtures.world(colors, chunks, Set.of());
    }
}
