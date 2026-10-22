package dev.orbis.atlas;

import dev.orbis.WorldSize;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasGlobeZoomTest {
    @Test void closestGlobeZoomShowsAFewHundredBlocksOnEveryWorldSize() {
        assertEquals(AtlasMapRaster.GLOBE_ZOOM_MAX, AtlasMapRaster.maxGlobeZoom(WorldSize.DEBUG_MIN_CHUNKS * 16));
        double previous = 0;
        for (WorldSize.Preset preset : WorldSize.Preset.values()) {
            int span = preset.size.overworldChunks() * 16;
            double zoom = AtlasMapRaster.maxGlobeZoom(span);
            assertTrue(zoom >= AtlasMapRaster.GLOBE_ZOOM_MAX && zoom >= previous, preset.label);
            // Blocks across the view's shorter side at the globe's centre: about two radii of span / (2 pi radius).
            assertTrue(span / (Math.PI * zoom) <= 1024, preset.label);
            previous = zoom;
        }
        assertTrue(AtlasMapRaster.maxGlobeZoom(Integer.MAX_VALUE) >= AtlasMapRaster.maxGlobeZoom(WorldSize.EARTH_CHUNKS * 16));
    }
}
