package dev.orbis.atlas;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrbisMinimapTest {
    private AtlasSnapshot world() {
        return AtlasFixtures.world(Map.of(), List.of(), Set.of());
    }

    @Test void flatMarkersUseCanonicalCoordinatesWithoutDrawingCopies() {
        assertArrayEquals(new double[]{-956, -956}, OrbisMinimap.project(world(), -1020, -1020,
                1020, 1020, 128, .5, false, 0, 0), 1e-9);
    }

    @Test void globeCentersPlayerAndHidesBackHemisphereMarkers() {
        double x = 128, z = -384;
        double yaw = -((x + 1024) / 2048 - .5) * Math.PI * 2;
        double pitch = (.5 - (z + 1024) / 2048) * Math.PI;
        assertArrayEquals(new double[]{64, 64}, OrbisMinimap.project(world(), x, z,
                x, z, 128, .5, true, yaw, pitch), 1e-9);
        assertNull(OrbisMinimap.project(world(), x - 1024, -z,
                x, z, 128, .5, true, yaw, pitch));
    }
}
