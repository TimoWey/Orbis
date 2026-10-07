package dev.orbis.debug;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DebugTerrainTest {
    @Test void tileCopiesItsPixelsAndAddressesNegativeCoordinates() {
        var colors = new ArrayList<Integer>();
        for (int i = 0; i < 16; i++) colors.add(0xff000000 | i);
        var cell = new DebugSnapshot.Cell(-1, -1, 0, colors);
        colors.set(15, 123);
        assertEquals(0xff00000f, cell.colorAt(-1, -1));
        assertEquals(0xff000000, cell.colorAt(-16, -16));
        assertEquals(0xff000004, cell.colorAt(-16, -12));
        assertThrows(UnsupportedOperationException.class, () -> cell.terrain().set(0, 123));
        assertThrows(IllegalArgumentException.class, () -> new DebugSnapshot.Cell(0, 0, 0, List.of(1)));
    }

    @Test void flatTerrainRemainsVisibleWithoutChunkOverlayAndKeepsSurfaceDetail() {
        var colors = new ArrayList<Integer>(Collections.nCopies(16, 0xff123456));
        colors.set(15, 0xffabcdef);
        var cell = new DebugSnapshot.Cell(-1, -1, 0xff001100, colors);
        var view = new DebugMapRaster.View(64, 64, -8, -8, 2, false, 0, 0, false, false);
        int[] pixels = new DebugMapRaster(64, 64).render(fixture(List.of(cell)), view);
        assertEquals(0xff123456, pixels[18 * 64 + 18]);
        assertEquals(0xffabcdef, pixels[46 * 64 + 46]);
        assertEquals(DebugMapRaster.BACKGROUND, pixels[50 * 64 + 50]);
    }

    @Test void globeUsesTerrainAcrossNegativeAndPositiveChunkBoundariesWithoutOverlay() {
        var cells = List.of(new DebugSnapshot.Cell(-1, -1, 0xff123456),
                new DebugSnapshot.Cell(0, -1, 0xffabcdef),
                new DebugSnapshot.Cell(-1, 0, 0xff223344),
                new DebugSnapshot.Cell(0, 0, 0xff556677));
        var view = new DebugMapRaster.View(64, 64, 0, 0, 1, true, 0, 0, false, false);
        int[] pixels = new DebugMapRaster(64, 64).render(fixture(cells), view);
        assertEquals(0xff123456, pixels[31 * 64 + 31]);
        assertEquals(0xffabcdef, pixels[31 * 64 + 32]);
        assertEquals(0xff223344, pixels[32 * 64 + 31]);
        assertEquals(0xff556677, pixels[32 * 64 + 32]);
        assertEquals(0, pixels[0]);
    }

    @Test void flatNeverDrawsCopiesBeyondTheCanonicalWorld() {
        var data = fixture(List.of(new DebugSnapshot.Cell(-64, 0, 0xffabcdef)));
        var raster = new DebugMapRaster(64, 64);
        var edge = new DebugMapRaster.View(64, 64, 1024, 8, 1, false, 0, 0, false, false);
        assertEquals(DebugMapRaster.BACKGROUND, raster.render(data, edge)[32 * 64 + 36]);
        var far = new DebugMapRaster.View(64, 64, 1024 + 2048 * 3, 8, 1, false, 0, 0, false, false);
        assertTrue(Arrays.stream(raster.render(data, far)).allMatch(p -> p == DebugMapRaster.BACKGROUND));
    }

    private static DebugSnapshot fixture(List<DebugSnapshot.Cell> chunks) {
        return new DebugSnapshot("minecraft:overworld", 123, "Custom", 1, -1024, -1024, 2048,
                true, 0, 64, 0, 10, chunks, Set.of(), List.of(), List.of(), Map.of(), 0,
                DebugSurvey.TOTAL, false, false, "", List.of(), false, 0, "Not checked yet");
    }

    @Test void ceilingLayerUsesLavaLevelAndRespectsCustomVerticalBounds() {
        assertEquals(32, DebugTerrain.sliceY(0, 255));
        assertEquals(32, DebugTerrain.sliceY(-64, 319));
        assertEquals(64, DebugTerrain.sliceY(64, 127));
        assertEquals(15, DebugTerrain.sliceY(-64, 15));
    }

    @Test void allFiveNetherBiomesStayDistinctUnderUniformRockOrLava() {
        var ids = List.of("nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas");
        var colors = ids.stream().map(id -> DebugMapMath.biomeColor("minecraft:" + id)).toList();
        assertEquals(5, colors.stream().distinct().count());
        for (int block : new int[] {0x700200, 0xff0000, 0x707070}) {
            var slices = colors.stream().map(biome -> DebugTerrain.sliceColor(biome, block)).toList();
            assertEquals(5, slices.stream().distinct().count());
            for (int i = 0; i < colors.size(); i++) {
                assertEquals(255, slices.get(i) >>> 24);
                for (int shift : new int[] {0, 8, 16})
                    assertTrue(Math.abs((slices.get(i) >> shift & 255) - (colors.get(i) >> shift & 255)) <= 64);
            }
        }
        for (int color : colors) assertEquals(color, DebugTerrain.sliceColor(color, null));
        assertNotEquals(DebugMapMath.biomeColor("minecraft:forest"), colors.get(2));
        assertNotEquals(DebugMapMath.biomeColor("minecraft:forest"), colors.get(3));
    }
}
