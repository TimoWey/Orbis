package dev.orbis.debug;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Small render fixture; production snapshots have one authoritative search state. */
final class AtlasFixtures {
    static DebugSnapshot archive(DebugSnapshot d, AtlasTileArchive archive) {
        return new DebugSnapshot(d.dimension(), d.seed(), d.preset(), d.mapRevision(), d.minX(), d.minZ(), d.span(),
                d.wrapped(), d.playerX(), d.playerY(), d.playerZ(), d.tickMillis(), d.chunks(), d.loaded(), d.biomes(),
                d.places(), d.surveyColors(), d.historyLimited(), d.error(), d.structures(), d.structuresScanning(),
                d.structureChecks(), d.structureState(), d.search(), d.structureCounting(), d.layers(), archive);
    }
    static DebugSnapshot world(Map<Integer, Integer> colors, List<DebugSnapshot.Cell> chunks, Set<Long> loaded) {
        return world(colors, chunks, loaded, List.of(), new DebugSnapshot.MapLayers(1, 1, 1, 1));
    }
    static DebugSnapshot world(Map<Integer, Integer> colors, List<DebugSnapshot.Cell> chunks, Set<Long> loaded,
            List<DebugSnapshot.Place> places, DebugSnapshot.MapLayers layers) {
        return new DebugSnapshot("minecraft:overworld", 123, "Custom", 1, -1024, -1024, 2048,
                true, 0, 64, 0, 10, chunks, loaded, List.of(), places, colors, false, "", List.of(),
                false, 0, "Not checked yet", new AtlasSearch.View(
                        new AtlasSearch.Saved(0, AtlasSearch.QUICK_MAX, false, 0, AtlasSearch.MAP_COARSE, false), false),
                false, layers, null);
    }
    private AtlasFixtures() {}
}
