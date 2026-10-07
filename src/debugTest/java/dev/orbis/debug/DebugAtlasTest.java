package dev.orbis.debug;

import dev.orbis.WorldSize;
import java.util.*;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DebugAtlasTest {
    @Test void reopeningSameWorldKeepsTheLiveMapButDimensionChangesClearIt() {
        UUID player = UUID.randomUUID();
        try {
            DebugSurvey.open(net.minecraft.world.level.Level.OVERWORLD, player);
            var live = fixture(Map.of(), List.of(), Set.of());
            DebugSurvey.snapshot = live;
            DebugSurvey.open(net.minecraft.world.level.Level.OVERWORLD, player);
            assertSame(live, DebugSurvey.snapshot);
            DebugSurvey.open(net.minecraft.world.level.Level.NETHER, player);
            assertNull(DebugSurvey.snapshot);
            DebugSurvey.snapshot = live;
            DebugSurvey.close();
            assertNull(DebugSurvey.snapshot);
        } finally {
            DebugSurvey.close();
        }
    }

    @Test void structureEvidenceNeverEquatesAnIncompleteSearchWithAbsence() {
        var unknown = new StructureAudit.Entry("stronghold", null, false, false);
        var absent = new StructureAudit.Entry("stronghold", null, false, true);
        var predicted = new StructureAudit.Entry("stronghold", new DebugSnapshot.Place("stronghold", 0, 32, 0), false, false);
        assertEquals("Unconfirmed", unknown.status());
        assertEquals("Not found in check", absent.status());
        assertFalse(StructureAudit.allFound(List.of(unknown, predicted)));
        assertFalse(StructureAudit.allFound(List.of(absent, predicted)));
        assertFalse(StructureAudit.allFound(List.of()));
        assertTrue(StructureAudit.allFound(List.of(predicted)));
        assertEquals("Predicted", predicted.status());
        // Completing a prediction check never downgrades stronger, loaded-world evidence.
        assertEquals("Observed", new StructureAudit.Entry("stronghold", predicted.location(), true, true).status());
        assertEquals("Predicted", new StructureAudit.Entry("stronghold", predicted.location(), false, true).status());
    }

    @Test void placementRegionsFindExactlyTheSameCandidatesAsEveryChunkIncludingNegativeEdges() {
        var spread = new net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement(
                32, 8, net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType.LINEAR, 10387312);
        for (long seed : new long[] {-8912, 0, 928314}) {
            int minX = -79, minZ = -35, width = 128;
            Set<ChunkPos> brute = new HashSet<>(), regions = new HashSet<>();
            for (int x = minX; x < minX + width; x++) for (int z = minZ; z < minZ + width; z++) {
                ChunkPos actual = new ChunkPos(x, z);
                if (actual.equals(spread.getPotentialStructureChunk(seed, x, z))) brute.add(actual);
            }
            var grid = new StructureAudit.Grid(minX, minZ, width, spread.spacing());
            for (long i = 0; i < grid.total; i++) {
                var origin = grid.at(i);
                var p = spread.getPotentialStructureChunk(seed, origin.x(), origin.z());
                if (p.x() >= minX && p.x() < minX + width && p.z() >= minZ && p.z() < minZ + width) regions.add(p);
            }
            assertEquals(brute, regions);
            assertFalse(brute.isEmpty());
        }
    }

    @Test void fullChunkFallbackUsesLongCountersAndKeepsEarthEndpointsInBounds() {
        int width = WorldSize.EARTH_CHUNKS, min = -width / 2;
        var grid = new StructureAudit.Grid(min, min, width, 1);
        assertEquals((long) width * width, grid.total);
        assertTrue(grid.total > Integer.MAX_VALUE);
        for (long index : new long[] {0, grid.total / 2, grid.total - 1}) {
            var point = grid.at(index);
            assertTrue(point.x() >= min && point.x() < min + width);
            assertTrue(point.z() >= min && point.z() < min + width);
        }
        assertThrows(IndexOutOfBoundsException.class, () -> grid.at(grid.total));
    }

    @Test void globeZoomChangesTheRenderedRadiusAndClipsAtTheViewport() {
        var data = fixture(Map.of(), List.of(), Set.of());
        var raster = new DebugMapRaster(128, 128);
        var normal = new DebugMapRaster.View(128, 128, 0, 0, 1, true, 0, 0, false, false);
        assertEquals(0, raster.render(data, normal)[0]);
        var zoomed = new DebugMapRaster.View(128, 128, 0, 0, 2, true, 0, 0, false, false);
        assertNotEquals(DebugMapRaster.BACKGROUND, raster.render(data, zoomed)[0]);
    }

    @Test void surveyCentersStayInsideEveryPresetWithoutOverflow() {
        for (WorldSize.Preset preset : WorldSize.Preset.values()) {
            for (int span : new int[] {preset.size.overworldChunks() * 16, preset.size.netherChunks() * 16}) {
                int min = -span / 2;
                int previous = min - 1;
                for (int i = 0; i < DebugSurvey.GRID; i++) {
                    int coordinate = DebugMapMath.sampleCoordinate(min, span, i, DebugSurvey.GRID);
                    assertTrue(coordinate >= min && coordinate < min + span);
                    assertTrue(coordinate > previous);
                    previous = coordinate;
                }
            }
        }
    }

    @Test void projectionRoundTripsVisibleLocationsAfterRotation() {
        for (double yaw : new double[] {-2.7, 0, 1.1, 8.2}) {
            for (double pitch : new double[] {-1.1, 0, .8}) {
                for (int x = 1; x < 20; x++) for (int y = 1; y < 10; y++) {
                    double u = x / 20.0, v = y / 10.0;
                    double[] xyz = DebugMapMath.globe(u, v, yaw, pitch);
                    if (xyz[2] <= .001) continue;
                    double[] result = DebugMapMath.unproject(xyz[0], xyz[1], yaw, pitch);
                    assertNotNull(result);
                    assertEquals(u, result[0], 1e-8);
                    assertEquals(v, result[1], 1e-8);
                }
            }
        }
    }

    @Test void outsideGlobeHasNoMapCoordinate() {
        assertNull(DebugMapMath.unproject(1, 1, 0, 0));
        assertEquals(.75, DebugMapMath.unit(-.25));
        assertEquals(0, DebugMapMath.unit(3));
    }

    @Test void unknownIsNeverReportedAsAbsentOrObserved() {
        var place = new DebugSnapshot.Place("plains", -128, 64, 256);
        assertEquals("[?]", new DebugSnapshot.BiomeEntry("plains", false, null).status());
        assertEquals("[~]", new DebugSnapshot.BiomeEntry("plains", false, place).status());
        assertEquals("[x]", new DebugSnapshot.BiomeEntry("plains", true, place).status());
    }

    @Test void coverageUsesTheUnionAndFiltersDoNotChangeTheTotal() {
        var location = new DebugSnapshot.Place("biome", 0, 64, 0);
        var entries = List.of(new DebugSnapshot.BiomeEntry("seen", true, location, 0),
                new DebugSnapshot.BiomeEntry("sampled", false, location, 100),
                new DebugSnapshot.BiomeEntry("both", true, location, 50),
                new DebugSnapshot.BiomeEntry("unknown", false, null, 0));
        var counts = BiomeOverview.counts(entries);
        assertEquals(new BiomeOverview.Counts(4, 3, 2, 2), counts);
        assertEquals(1, counts.unknown());
        assertFalse(counts.allFound());
        assertEquals(List.of("unknown"), BiomeOverview.select(entries, BiomeOverview.Filter.UNCONFIRMED, BiomeOverview.Sort.HITS)
                .stream().map(DebugSnapshot.BiomeEntry::id).toList());
        assertEquals(List.of("sampled", "both", "seen"), BiomeOverview.select(entries, BiomeOverview.Filter.FOUND, BiomeOverview.Sort.HITS)
                .stream().map(DebugSnapshot.BiomeEntry::id).toList());
        assertFalse(BiomeOverview.counts(List.of()).allFound());
        assertTrue(BiomeOverview.counts(entries.subList(0, 3)).allFound());
    }

    @Test void presetsFollowCurrentWorldSizesRatherThanHardcodedWidths() {
        for (var preset : WorldSize.Preset.values()) assertEquals(preset.label, BiomeOverview.preset(preset.size.overworldChunks()));
        assertEquals("Custom", BiomeOverview.preset(WorldSize.Preset.TINY.size.overworldChunks() + 16));
        assertEquals("Unwrapped", BiomeOverview.preset(0));
    }

    @Test void rasterClipsOffscreenGeometryAndUsesNegativeChunkCoordinates() {
        var data = fixture(Map.of(), List.of(new DebugSnapshot.Cell(-1, -1, 0xffabcdef)), Set.of(ChunkPos.pack(-1, -1)));
        var raster = new DebugMapRaster(128, 128);
        var view = new DebugMapRaster.View(128, 128, 0, 0, 128.0 / 2048, false, 0, 0, true, true);
        int[] pixels = raster.render(data, view);
        assertEquals(0xffabcdef, pixels[63 * 128 + 63]);
        assertEquals(DebugMapRaster.BACKGROUND, pixels[50 * 128 + 50]);
        var farAway = new DebugMapRaster.View(128, 128, 1e12, -1e12, 100, false, 0, 0, true, true);
        assertTrue(Arrays.stream(raster.render(data, farAway)).allMatch(p -> p == DebugMapRaster.BACKGROUND));
    }

    @Test void completedGlobeUsesSampleColorsAndLeavesOutsidePixelsEmpty() {
        Map<Integer, Integer> colors = new HashMap<>();
        for (int i = 0; i < DebugSurvey.GRID * DebugSurvey.GRID; i++) colors.put(i, 0xff4182a6);
        var raster = new DebugMapRaster(128, 128);
        var view = new DebugMapRaster.View(128, 128, 0, 0, 1, true, 1.2, -.6, false, false);
        int[] pixels = raster.render(fixture(colors, List.of(), Set.of()), view);
        assertEquals(0xff4182a6, pixels[64 * 128 + 64]);
        assertEquals(0, pixels[0]);
        assertEquals(128 * 128, pixels.length);
    }

    @Test void displayResolutionKeepsDetailsSmallerThanAGuiPixel() {
        // Two adjacent sample cells occupy less than one GUI pixel, but remain distinct
        // when the atlas raster is sized for an actual high-DPI display.
        int first = 10 * DebugSurvey.GRID + 10;
        var data = fixture(Map.of(first, 0xff123456, first + 1, 0xffabcdef), List.of(), Set.of());
        var view = new DebugMapRaster.View(64, 64, 0, 0, 64.0 / 2048, false, 0, 0, false, false);
        int[] pixels = new DebugMapRaster(512, 512).render(data, view);
        int cellPixels = 512 / DebugSurvey.GRID;
        int offset = (10 * cellPixels) * 512 + 10 * cellPixels;
        assertEquals(0xff123456, pixels[offset]);
        assertEquals(0xffabcdef, pixels[offset + cellPixels]);
        assertEquals(DebugMapRaster.BACKGROUND, pixels[offset + 2 * cellPixels]);
    }

    private static DebugSnapshot fixture(Map<Integer, Integer> colors, List<DebugSnapshot.Cell> chunks, Set<Long> loaded) {
        return AtlasFixtures.world(colors, chunks, loaded);
    }
}
