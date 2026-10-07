package dev.orbis.debug;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DebugMapKeyTest {
    private static DebugSnapshot snapshot(DebugSnapshot.MapLayers layers) {
        return AtlasFixtures.world(Map.of(), List.of(), Set.of(), List.of(), layers);
    }
    private static DebugMapRaster.View view(boolean chunks, boolean places) {
        return new DebugMapRaster.View(128, 128, 0, 0, .5, false, 0, 0, chunks, places);
    }

    @Test void hiddenOverlayChangesDoNotInvalidateTerrainButVisibleChangesDo() {
        var before = snapshot(new DebugSnapshot.MapLayers(1, 2, 3, 4));
        var loadedChanged = snapshot(new DebugSnapshot.MapLayers(1, 2, 5, 4));
        var placesChanged = snapshot(new DebugSnapshot.MapLayers(1, 2, 3, 6));
        for (var after : List.of(loadedChanged, placesChanged))
            assertEquals(DebugMapKey.of(before, view(false, false)), DebugMapKey.of(after, view(false, false)));
        assertNotEquals(DebugMapKey.of(before, view(true, false)), DebugMapKey.of(loadedChanged, view(true, false)));
        assertNotEquals(DebugMapKey.of(before, view(false, true)), DebugMapKey.of(placesChanged, view(false, true)));
        assertEquals(DebugMapKey.of(before, view(true, false)), DebugMapKey.of(placesChanged, view(true, false)));
        assertEquals(DebugMapKey.of(before, view(false, true)), DebugMapKey.of(loadedChanged, view(false, true)));
    }

    @Test void terrainAndPreviewChangesAlwaysInvalidateTexture() {
        var before = snapshot(new DebugSnapshot.MapLayers(1, 2, 3, 4));
        for (var layers : List.of(new DebugSnapshot.MapLayers(5, 2, 3, 4), new DebugSnapshot.MapLayers(1, 6, 3, 4)))
            assertNotEquals(DebugMapKey.of(before, view(false, false)), DebugMapKey.of(snapshot(layers), view(false, false)));
    }

    @Test void onlyCameraCoordinatesUsedByProjectionInvalidateItsTexture() {
        var data = snapshot(new DebugSnapshot.MapLayers(1, 2, 3, 4));
        var flat = view(false, false);
        var irrelevantRotation = new DebugMapRaster.View(128, 128, 0, 0, .5, false, 1, 2, false, false);
        assertEquals(DebugMapKey.of(data, flat), DebugMapKey.of(data, irrelevantRotation));
        var globe = new DebugMapRaster.View(128, 128, 0, 0, 1, true, 1, 2, false, false);
        var irrelevantCenter = new DebugMapRaster.View(128, 128, 32, 48, 1, true, 1, 2, false, false);
        assertEquals(DebugMapKey.of(data, globe), DebugMapKey.of(data, irrelevantCenter));
        assertNotEquals(DebugMapKey.of(data, flat), DebugMapKey.of(data, globe));
    }
}
