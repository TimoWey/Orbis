package dev.orbis.debug;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasUnexploredTerrainTest {
    private static final Set<Integer> INKS = Set.of(AtlasUnknownTerrain.WATER, AtlasUnknownTerrain.LAND,
            AtlasUnknownTerrain.WOODED, AtlasUnknownTerrain.DRY);

    @Test void unsavedTerrainIsTheSameFictionalMapRegardlessOfBiomeSamples() {
        var samples = new HashMap<Integer, Integer>();
        for (int i = 0; i < DebugSurvey.GRID * DebugSurvey.GRID; i++)
            samples.put(i, i % 2 == 0 ? 0xff00ff00 : 0xff0000ff);
        var empty = AtlasFixtures.world(Map.of(), List.of(), Set.of());
        var sampled = AtlasFixtures.world(samples, List.of(), Set.of());
        for (boolean globe : new boolean[] {false, true}) {
            var view = new DebugMapRaster.View(128, 128, 0, 0, globe ? 1 : 128.0 / 2048,
                    globe, .7, -.4, false, false);
            var expected = new DebugMapRaster(128, 128).render(empty, view);
            var actual = new DebugMapRaster(128, 128).render(sampled, view);
            assertArrayEquals(expected, actual);
            var shades = new HashSet<Integer>();
            for (int y = 8; y < 120; y++) for (int x = 8; x < 120; x++) {
                int color = actual[y * 128 + x];
                if (color == 0) continue; // Outside the globe is transparent.
                assertEquals(255, color >>> 24);
                assertTrue(INKS.contains(color));
                shades.add(color);
            }
            assertTrue(shades.size() >= 2, "The fictional map must show broad water and land regions");
        }
    }

    @Test void fictionalMapStaysAnchoredWhenTheMinimapWindowMoves() {
        var empty = AtlasFixtures.world(Map.of(), List.of(), Set.of());
        var left = new DebugMapRaster(128, 128).render(empty,
                new DebugMapRaster.View(128, 128, 0, 0, 1, false, 0, 0, false, false));
        var right = new DebugMapRaster(128, 128).render(empty,
                new DebugMapRaster.View(128, 128, 64, 0, 1, false, 0, 0, false, false));
        for (int y = 0; y < 128; y++) for (int x = 0; x < 64; x++)
            assertEquals(left[y * 128 + x + 64], right[y * 128 + x]);
        assertTrue(Arrays.stream(left).allMatch(INKS::contains));
    }

    @Test void fictionalMapRepeatsAcrossNegativeCoordinatesAndKeepsFlatInksAtOverviewScale() {
        for (int level = 0; level < 7; level++) {
            for (int z = -300; z < 300; z += 17) for (int x = -300; x < 300; x += 19)
            {
                assertEquals(AtlasUnknownTerrain.color(x, z, level), AtlasUnknownTerrain.color(x + 4096, z - 4096, level));
                assertTrue(INKS.contains(AtlasUnknownTerrain.color(x, z, level)));
            }
        }
        assertEquals(0, AtlasUnknownTerrain.level(.25));
        assertEquals(1, AtlasUnknownTerrain.level(128));
        assertEquals(6, AtlasUnknownTerrain.level(1e9));
    }

    @Test void fictionalGeographyHasOnlyFourFlatInksAndPaleGrayWater() {
        var colors = new HashSet<Integer>();
        for (int z = 0; z < 4096; z += 64) for (int x = 0; x < 4096; x += 64) {
            int color = AtlasUnknownTerrain.color(x, z, 0);
            colors.add(color);
            assertEquals(color, AtlasUnknownTerrain.color(x + 63, z + 63, 0), "No fine texture within a map cell");
        }
        assertEquals(INKS, colors);
        int water = AtlasUnknownTerrain.WATER;
        assertEquals(water & 255, water >> 8 & 255);
        assertEquals(water & 255, water >> 16 & 255);
        for (int land : new int[]{AtlasUnknownTerrain.LAND, AtlasUnknownTerrain.WOODED, AtlasUnknownTerrain.DRY})
            assertTrue((water & 255) > Math.max(land & 255, Math.max(land >> 8 & 255, land >> 16 & 255)));
    }

    @Test void cachedMinimapDoesNotRebuildWhenOnlyBiomeSamplingProgresses() {
        var jobs = new ArrayDeque<Runnable>();
        var empty = AtlasFixtures.world(Map.of(), List.of(), Set.of());
        var sampled = AtlasFixtures.world(Map.of(0, 0xffff0000), List.of(), Set.of(), List.of(),
                new DebugSnapshot.MapLayers(1, 9000, 1, 1));
        try (var cache = new AtlasMinimapCache(jobs::add)) {
            cache.update(empty, 0, 0, 256);
            jobs.remove().run();
            var ready = cache.update(empty, 0, 0, 256);
            assertNotNull(ready);
            assertSame(ready, cache.update(sampled, 0, 0, 256));
            assertTrue(jobs.isEmpty());
        }
    }

    @Test void storedTerrainKeepsItsExactColorsAfterUnloadingOverTheFictionalBackground() {
        int[] colors = new int[256];
        for (int i = 0; i < colors.length; i++) colors[i] = 0xff800000 | i;
        var cell = new DebugSnapshot.Cell(-1, -1, colors[0], colors);
        var data = AtlasFixtures.world(Map.of(0, 0xff0000ff), List.of(cell), Set.of());
        var view = new DebugMapRaster.View(64, 64, -8, -8, 2, false, 0, 0, false, false);
        var pixels = new DebugMapRaster(64, 64).render(data, view);
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            assertEquals(colors[z * 16 + x], pixels[(16 + z * 2) * 64 + 16 + x * 2]);
        int unknown = pixels[8 * 64 + 8];
        assertTrue(INKS.contains(unknown));
    }
}
