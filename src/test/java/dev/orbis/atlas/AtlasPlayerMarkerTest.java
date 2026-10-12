package dev.orbis.atlas;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasPlayerMarkerTest {
    @Test void usesOriginalMinecraftMapSpritesAndSixteenHeadings() {
        assertEquals("minecraft:textures/atlas/map_decorations.png", AtlasPlayerMarker.PLAYER.atlasLocation().toString());
        assertEquals("minecraft:player", AtlasPlayerMarker.PLAYER.texture().toString());
        assertEquals("minecraft:frame", AtlasPlayerMarker.OTHER.texture().toString());
        for (int h = 0; h < 16; h++) {
            double angle = h * Math.PI / 8;
            assertEquals(h, AtlasPlayerMarker.heading(new double[]{Math.sin(angle), -Math.cos(angle)}));
        }
        assertEquals(8, AtlasPlayerMarker.heading(AtlasPlayerMarker.direction(false, 0, 0, 0, 0, 0)));
        assertEquals(12, AtlasPlayerMarker.heading(AtlasPlayerMarker.direction(false, 0, 0, 0, 0, 90)));
        assertEquals(0, AtlasPlayerMarker.heading(AtlasPlayerMarker.direction(false, 0, 0, 0, 0, 180)));
        assertEquals(4, AtlasPlayerMarker.heading(AtlasPlayerMarker.direction(false, 0, 0, 0, 0, 270)));
    }

    @Test void globeMarkerUsesTheDisplayedTerrainProjectionAtEveryRotationAndZoom() {
        var data = AtlasFixtures.world(java.util.Map.of(), java.util.List.of(), java.util.Set.of());
        for (double yaw : new double[]{-9.1, 0, 2.8}) for (double pitch : new double[]{-4, .7, 8})
            for (double zoom : new double[]{.5, 1, 3}) {
                var displayed = new AtlasMapRaster.View(192, 128, 0, 0, zoom, true, yaw, pitch, false, false);
                double radius = 60 * zoom;
                for (double nx : new double[]{-.6, 0, .6}) for (double ny : new double[]{-.6, 0, .6}) {
                    var uv = AtlasMapMath.unproject(nx, ny, displayed.yaw(), displayed.pitch());
                    var point = AtlasPlayerMarker.project(data, displayed,
                            data.minX() + uv[0] * data.span(), data.minZ() + uv[1] * data.span());
                    assertNotNull(point);
                    assertEquals(96 + nx * radius, point[0], 1e-8);
                    assertEquals(64 - ny * radius, point[1], 1e-8);
                }
            }
    }

    @Test void followingGlobeMarkerStaysCenteredAcrossSubBlockMovementAndWorldSeams() {
        var data = AtlasFixtures.world(java.util.Map.of(), java.util.List.of(), java.util.Set.of());
        for (double x : new double[]{-1024.1, -1024, -1023.9, 0, 3.99, 4.01, 1023.99, 1024.01})
            for (double z : new double[]{-1024.01, -.001, 0, .001, 1024.01}) {
                double yaw = -((x - data.minX()) / data.span() - .5) * Math.PI * 2;
                double pitch = (.5 - (z - data.minZ()) / data.span()) * Math.PI;
                var displayed = new AtlasMapRaster.View(128, 128, x, z, 1, true, yaw, pitch, false, false);
                var point = AtlasPlayerMarker.project(data, displayed, displayed.centerX(), displayed.centerZ());
                assertNotNull(point);
                assertEquals(64, Math.round(point[0]));
                assertEquals(64, Math.round(point[1]));
            }
    }

    @Test void minecraftYawMatchesCompassInFlatAndCenteredGlobe() {
        for (boolean globe : new boolean[]{false, true}) {
            for (double[] position : new double[][]{{.5, .5}, {.01, .99}, {.8, .1}}) {
                double yaw = -(position[0] - .5) * 2 * Math.PI;
                double pitch = (.5 - position[1]) * Math.PI;
                for (double[] heading : new double[][]{{0, 0, 1}, {90, -1, 0}, {180, 0, -1}, {270, 1, 0}}) {
                    var direction = AtlasPlayerMarker.direction(globe, position[0], position[1], yaw, pitch, heading[0]);
                    assertEquals(heading[1], direction[0], 1e-7);
                    assertEquals(heading[2], direction[1], 1e-7);
                }
                var center = AtlasMapMath.globe(position[0], position[1], yaw, pitch);
                assertArrayEquals(new double[]{0, 0, 1}, center, 1e-7);
            }
        }
    }
    @Test void offCenterGlobeHeadingFollowsProjectedTerrainTangent() {
        var direction = AtlasPlayerMarker.direction(true, .62, .6, .1, -.2, 45);
        var from = AtlasMapMath.globe(.62, .6, .1, -.2);
        var to = AtlasMapMath.globe(.62 - .00001, .6 + .00001, .1, -.2);
        double length = Math.hypot(to[0] - from[0], to[1] - from[1]);
        assertEquals((to[0] - from[0]) / length, direction[0], 1e-4);
        assertEquals((from[1] - to[1]) / length, direction[1], 1e-4);
    }
}
