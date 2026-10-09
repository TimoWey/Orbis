package dev.orbis.atlas;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasMapKeyTest {
    private static AtlasSnapshot snapshot(AtlasSnapshot.MapLayers layers) {
        return AtlasFixtures.world(Map.of(), List.of(), Set.of(), List.of(), layers);
    }
    private static AtlasMapRaster.View view(boolean chunks, boolean places) {
        return new AtlasMapRaster.View(128, 128, 0, 0, .5, false, 0, 0, chunks, places);
    }

    @Test void hiddenOverlayChangesDoNotInvalidateTerrainButVisibleChangesDo() {
        var before = snapshot(new AtlasSnapshot.MapLayers(1, 2, 3, 4));
        var loadedChanged = snapshot(new AtlasSnapshot.MapLayers(1, 2, 5, 4));
        var placesChanged = snapshot(new AtlasSnapshot.MapLayers(1, 2, 3, 6));
        for (var after : List.of(loadedChanged, placesChanged))
            assertEquals(AtlasMapKey.of(before, view(false, false)), AtlasMapKey.of(after, view(false, false)));
        assertNotEquals(AtlasMapKey.of(before, view(true, false)), AtlasMapKey.of(loadedChanged, view(true, false)));
        assertNotEquals(AtlasMapKey.of(before, view(false, true)), AtlasMapKey.of(placesChanged, view(false, true)));
        assertEquals(AtlasMapKey.of(before, view(true, false)), AtlasMapKey.of(placesChanged, view(true, false)));
        assertEquals(AtlasMapKey.of(before, view(false, true)), AtlasMapKey.of(loadedChanged, view(false, true)));
    }

    @Test void terrainChangesInvalidateTextureButHiddenBiomeSamplesDoNot() {
        var before = snapshot(new AtlasSnapshot.MapLayers(1, 2, 3, 4));
        assertNotEquals(AtlasMapKey.of(before, view(false, false)),
                AtlasMapKey.of(snapshot(new AtlasSnapshot.MapLayers(5, 2, 3, 4)), view(false, false)));
        assertEquals(AtlasMapKey.of(before, view(false, false)),
                AtlasMapKey.of(snapshot(new AtlasSnapshot.MapLayers(1, 6, 3, 4)), view(false, false)));
    }

    @Test void onlyCameraCoordinatesUsedByProjectionInvalidateItsTexture() {
        var data = snapshot(new AtlasSnapshot.MapLayers(1, 2, 3, 4));
        var flat = view(false, false);
        var irrelevantRotation = new AtlasMapRaster.View(128, 128, 0, 0, .5, false, 1, 2, false, false);
        assertEquals(AtlasMapKey.of(data, flat), AtlasMapKey.of(data, irrelevantRotation));
        var globe = new AtlasMapRaster.View(128, 128, 0, 0, 1, true, 1, 2, false, false);
        var irrelevantCenter = new AtlasMapRaster.View(128, 128, 32, 48, 1, true, 1, 2, false, false);
        assertEquals(AtlasMapKey.of(data, globe), AtlasMapKey.of(data, irrelevantCenter));
        assertNotEquals(AtlasMapKey.of(data, flat), AtlasMapKey.of(data, globe));
    }
}
