package dev.orbis.atlas;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasTerrainTest {
    @Test void flatAlwaysUsesOriginalBlockColorsAcrossZoomAndDisplayScales() {
        var data = fixture(detailedChunks()); // Cached terrain remains detailed after chunks unload.
        for (int factor : new int[] {1, 2}) for (double scale : new double[] {.125, .5, 1, 2}) {
            int size = 64 * factor;
            var view = new AtlasMapRaster.View(64, 64, -7.25, 3.25, scale, false, 0, 0, false, false);
            int[] pixels = new AtlasMapRaster(size, size).render(data, view);
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                int bx = (int) Math.floor(-7.25 + ((x + .5) / factor - 32) / scale);
                int bz = (int) Math.floor(3.25 + ((y + .5) / factor - 32) / scale);
                int expected = bx >= -16 && bx < 16 && bz >= -16 && bz < 16
                        ? blockColor(bx, bz) : AtlasUnknownTerrain.color(
                                2 * (-7.25 + ((x + .5) / factor - 32) / scale),
                                2 * (3.25 + ((y + .5) / factor - 32) / scale),
                                AtlasUnknownTerrain.level(2 / (factor * scale)));
                assertEquals(expected, pixels[y * size + x], "Flat scale=" + scale + " factor=" + factor);
            }
        }
    }

    @Test void globeAlwaysUsesOriginalBlockColorsAcrossZoomAndDisplayScales() {
        var data = fixture(detailedChunks());
        for (int factor : new int[] {1, 2}) for (double scale : new double[] {.5, 1, 2}) {
            int size = 64 * factor;
            var view = new AtlasMapRaster.View(64, 64, 0, 0, scale, true, 0, 0, false, false);
            int[] pixels = new AtlasMapRaster(size, size).render(data, view);
            for (int y = size / 2 - 1; y <= size / 2; y++) for (int x = size / 2 - 1; x <= size / 2; x++) {
                double[] uv = AtlasMapMath.unproject(((x + .5) / factor - 32) / (28 * scale),
                        (32 - (y + .5) / factor) / (28 * scale), 0, 0);
                int bx = (int) Math.floor(-1024 + uv[0] * 2048);
                int bz = (int) Math.floor(-1024 + uv[1] * 2048);
                assertEquals(blockColor(bx, bz), pixels[y * size + x], "Globe scale=" + scale + " factor=" + factor);
            }
        }
    }

    private static List<AtlasSnapshot.Cell> detailedChunks() {
        var chunks = new ArrayList<AtlasSnapshot.Cell>();
        for (int cz = -1; cz <= 0; cz++) for (int cx = -1; cx <= 0; cx++) {
            int[] colors = new int[256];
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                colors[z * 16 + x] = blockColor(cx * 16 + x, cz * 16 + z);
            chunks.add(new AtlasSnapshot.Cell(cx, cz, 0xff987654, colors));
        }
        return List.copyOf(chunks);
    }

    private static int blockColor(int x, int z) {
        return 0xff000000 | ((x + 16) << 16) | ((z + 16) << 8) | ((x + z + 32) & 255);
    }

    @Test void tileCopiesItsPixelsAndAddressesNegativeCoordinates() {
        var colors = new ArrayList<Integer>();
        for (int i = 0; i < 16; i++) colors.add(0xff000000 | i);
        var cell = new AtlasSnapshot.Cell(-1, -1, 0, colors);
        colors.set(15, 123);
        assertEquals(0xff00000f, cell.colorAt(-1, -1));
        assertEquals(0xff000000, cell.colorAt(-16, -16));
        assertEquals(0xff000004, cell.colorAt(-16, -12));
        int[] copy = cell.copyTerrain();
        copy[0] = 123;
        assertEquals(0xff000000, cell.terrainPixel(0));
        assertThrows(IllegalArgumentException.class, () -> new AtlasSnapshot.Cell(0, 0, 0, List.of(1)));
    }

    @Test void flatTerrainRemainsVisibleWithoutChunkOverlayAndKeepsSurfaceDetail() {
        var colors = new ArrayList<Integer>(Collections.nCopies(16, 0xff123456));
        colors.set(15, 0xffabcdef);
        var cell = new AtlasSnapshot.Cell(-1, -1, 0xff001100, colors);
        var view = new AtlasMapRaster.View(64, 64, -8, -8, 2, false, 0, 0, false, false);
        int[] pixels = new AtlasMapRaster(64, 64).render(fixture(List.of(cell)), view);
        assertEquals(0xff123456, pixels[18 * 64 + 18]);
        assertEquals(0xffabcdef, pixels[46 * 64 + 46]);
        assertEquals(AtlasUnknownTerrain.color(2.5, 2.5, 0), pixels[50 * 64 + 50]);
    }

    @Test void globeUsesTerrainAcrossNegativeAndPositiveChunkBoundariesWithoutOverlay() {
        var cells = List.of(new AtlasSnapshot.Cell(-1, -1, 0xff123456),
                new AtlasSnapshot.Cell(0, -1, 0xffabcdef),
                new AtlasSnapshot.Cell(-1, 0, 0xff223344),
                new AtlasSnapshot.Cell(0, 0, 0xff556677));
        var view = new AtlasMapRaster.View(64, 64, 0, 0, 1, true, 0, 0, false, false);
        int[] pixels = new AtlasMapRaster(64, 64).render(fixture(cells), view);
        assertEquals(0xff123456, pixels[31 * 64 + 31]);
        assertEquals(0xffabcdef, pixels[31 * 64 + 32]);
        assertEquals(0xff223344, pixels[32 * 64 + 31]);
        assertEquals(0xff556677, pixels[32 * 64 + 32]);
        assertEquals(0, pixels[0]);
    }

    @Test void flatContinuesPastTheSeam() {
        var data = fixture(List.of(new AtlasSnapshot.Cell(-64, 0, 0xffabcdef)));
        var raster = new AtlasMapRaster(64, 64);
        // Block 1028 lies past the east seam, in the world's westernmost chunk.
        var edge = new AtlasMapRaster.View(64, 64, 1024, 8, 1, false, 0, 0, false, false);
        assertEquals(0xffabcdef, raster.render(data, edge)[32 * 64 + 36]);
    }

    private static AtlasSnapshot fixture(List<AtlasSnapshot.Cell> chunks) {
        return AtlasFixtures.world(Map.of(), chunks, Set.of());
    }

    @Test void ceilingLayerUsesLavaLevelAndRespectsCustomVerticalBounds() {
        assertEquals(32, AtlasTerrain.sliceY(0, 255));
        assertEquals(32, AtlasTerrain.sliceY(-64, 319));
        assertEquals(64, AtlasTerrain.sliceY(64, 127));
        assertEquals(15, AtlasTerrain.sliceY(-64, 15));
    }

    @Test void observedNetherBlocksKeepActualColorsAndAirIsNeutral() {
        for (int block : new int[] {0x700200, 0xff0000, 0x707070})
            assertEquals(0xff000000 | block, AtlasTerrain.sliceColor(block, true));
        assertEquals(AtlasTerrain.EMPTY_SLICE_COLOR, AtlasTerrain.sliceColor(0, false));
    }

    @Test void primitiveTilesOwnTheirOriginalPixels() {
        int[] pixels = new int[256];
        Arrays.fill(pixels, 0xff123456);
        var cell = new AtlasSnapshot.Cell(-1, -1, 0, pixels);
        pixels[0] = 0;
        assertEquals(256, cell.terrainSize());
        assertEquals(0xff123456, cell.colorAt(-16, -16));
        assertTrue(cell.sameTerrain(new AtlasSnapshot.Cell(10, 10, 0, cell.copyTerrain())));
        assertFalse(cell.sameTerrain(new AtlasSnapshot.Cell(-1, -1, 0, pixels)));
    }
}
