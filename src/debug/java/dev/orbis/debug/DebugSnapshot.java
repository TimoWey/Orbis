package dev.orbis.debug;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable hand-off from the integrated server to the render thread. */
public record DebugSnapshot(String dimension, long seed, String preset, long mapRevision, int minX, int minZ, int span,
        boolean wrapped, int playerX, int playerY, int playerZ, double tickMillis,
        List<Cell> chunks, Set<Long> loaded, List<BiomeEntry> biomes, List<Place> places,
        Map<Integer, Integer> surveyColors, int surveyDone, int surveyTotal, boolean scanning,
        boolean historyLimited, String error, List<StructureAudit.Entry> structures,
        boolean structuresScanning, long structureChecks, String structureState) {
    public record Cell(int x, int z, int color) {}
    public record Place(String label, int x, int y, int z) {}
    public record BiomeEntry(String id, boolean observed, Place location, int sampleHits) {
        public BiomeEntry(String id, boolean observed, Place location) { this(id, observed, location, 0); }
        public boolean found() { return observed || location != null; }
        public String status() { return observed ? "[x]" : location != null ? "[~]" : "[?]"; }
    }
}
