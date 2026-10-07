package dev.orbis.debug;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasPlayerMarkerTest {
    @Test void minecraftYawMatchesCompassInFlatAndCenteredGlobe() {
        for (boolean globe : new boolean[]{false, true}) {
            for (double[] position : new double[][]{{.5, .5}, {.01, .99}, {.8, .1}}) {
                double yaw = -(position[0] - .5) * 2 * Math.PI;
                double pitch = (.5 - position[1]) * Math.PI;
                for (double[] heading : new double[][]{{0, 0, 1}, {90, -1, 0}, {180, 0, -1}, {270, 1, 0}}) {
                    var direction = AtlasPlayerMarker.direction(globe, position[0], position[1], yaw, pitch, heading[0]);
                    assertEquals(heading[1], direction[0], 1e-7);
                    assertEquals(heading[2], direction[1], 1e-7);
                    assertTrue(AtlasPlayerMarker.filled(direction[0] * 6, direction[1] * 6, direction[0], direction[1]));
                    assertFalse(AtlasPlayerMarker.filled(-direction[0] * 6, -direction[1] * 6, direction[0], direction[1]));
                }
                var center = DebugMapMath.globe(position[0], position[1], yaw, pitch);
                assertArrayEquals(new double[]{0, 0, 1}, center, 1e-7);
            }
        }
    }
    @Test void offCenterGlobeHeadingFollowsProjectedTerrainTangent() {
        var direction = AtlasPlayerMarker.direction(true, .62, .6, .1, -.2, 45);
        var from = DebugMapMath.globe(.62, .6, .1, -.2);
        var to = DebugMapMath.globe(.62 - .00001, .6 + .00001, .1, -.2);
        double length = Math.hypot(to[0] - from[0], to[1] - from[1]);
        assertEquals((to[0] - from[0]) / length, direction[0], 1e-4);
        assertEquals((from[1] - to[1]) / length, direction[1], 1e-4);
    }
}
