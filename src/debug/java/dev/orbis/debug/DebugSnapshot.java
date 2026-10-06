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
    /** Sixteen surface samples per chunk; copied before crossing to the render thread. */
    public record Cell(int x, int z, int color, List<Integer> terrain) {
        public Cell {
            terrain = List.copyOf(terrain);
            if (!terrain.isEmpty() && terrain.size() != 16) throw new IllegalArgumentException("Expected 4 x 4 terrain samples");
        }
        public Cell(int x, int z, int color) { this(x, z, color, List.of()); }
        public int colorAt(int blockX, int blockZ) {
            return terrain.isEmpty() ? color : terrain.get((blockZ & 15) / 4 * 4 + (blockX & 15) / 4);
        }
    }
    public record Place(String label, int x, int y, int z) {}
    public record BiomeEntry(String id, boolean observed, Place location, int sampleHits) {
        public BiomeEntry(String id, boolean observed, Place location) { this(id, observed, location, 0); }
        public boolean found() { return observed || location != null; }
        public String status() { return observed ? "[x]" : location != null ? "[~]" : "[?]"; }
    }
}
