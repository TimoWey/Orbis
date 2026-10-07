package dev.orbis.debug;

import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/** Immutable hand-off from the integrated server to the render thread. */
public record DebugSnapshot(String dimension, long seed, String preset, long mapRevision, int minX, int minZ, int span,
        boolean wrapped, int playerX, int playerY, int playerZ, double tickMillis,
        List<Cell> chunks, Set<Long> loaded, List<BiomeEntry> biomes, List<Place> places,
        Map<Integer, Integer> surveyColors,
        boolean historyLimited, String error, List<StructureAudit.Entry> structures,
        boolean structuresScanning, long structureChecks, String structureState, AtlasSearch.View search,
        boolean structureCounting, MapLayers layers) {
    /** Independent layer revisions prevent hidden overlays from invalidating terrain. */
    public record MapLayers(long terrain, long biomes, long loaded, long places) {}

    /** Block-resolution surface samples per chunk; copied before crossing to the render thread. */
    public static final class Cell {
        private final int x, z, color;
        private final int[] terrain;
        private final int[] coarse = new int[16];
        private final int average;
        public Cell(int x, int z, int color, List<Integer> terrain) {
            this(x, z, color, terrain.stream().mapToInt(Integer::intValue).toArray());
        }
        public Cell(int x, int z, int color, int[] terrain) {
            this.x = x; this.z = z; this.color = color;
            this.terrain = terrain.clone();
            if (terrain.length != 0 && terrain.length != 16 && terrain.length != 256)
                throw new IllegalArgumentException("Expected 4 x 4 or 16 x 16 terrain samples");
            for (int tz = 0; tz < 4; tz++) for (int tx = 0; tx < 4; tx++) {
                int r = 0, g = 0, b = 0;
                for (int dz = 0; dz < 4; dz++) for (int dx = 0; dx < 4; dx++) {
                    int c = colorAt(tx * 4 + dx, tz * 4 + dz);
                    r += c >> 16 & 255; g += c >> 8 & 255; b += c & 255;
                }
                coarse[tz * 4 + tx] = 0xff000000 | (r / 16 << 16) | (g / 16 << 8) | b / 16;
            }
            int r = 0, g = 0, b = 0;
            for (int c : coarse) { r += c >> 16 & 255; g += c >> 8 & 255; b += c & 255; }
            average = 0xff000000 | (r / 16 << 16) | (g / 16 << 8) | b / 16;
        }
        public Cell(int x, int z, int color) { this(x, z, color, new int[0]); }
        public int x() { return x; }
        public int z() { return z; }
        public int color() { return color; }
        public int terrainSize() { return terrain.length; }
        public int terrainPixel(int index) { return terrain[index]; }
        public int[] copyTerrain() { return terrain.clone(); }
        public boolean sameTerrain(Cell other) {
            return color == other.color && Arrays.equals(terrain, other.terrain);
        }
        public int colorAt(int blockX, int blockZ) {
            return terrain.length == 0 ? color : terrain.length == 256 ? terrain[(blockZ & 15) * 16 + (blockX & 15)]
                    : terrain[(blockZ & 15) / 4 * 4 + (blockX & 15) / 4];
        }
        public int colorAt(int x, int z, int step) {
            return step >= 16 ? average : step >= 4 ? coarse[(z & 15) / 4 * 4 + (x & 15) / 4] : colorAt(x, z);
        }
    }
    public record Place(String label, int x, int y, int z) {}
    public record BiomeEntry(String id, boolean observed, Place location, int sampleHits) {
        public BiomeEntry(String id, boolean observed, Place location) { this(id, observed, location, 0); }
        public boolean found() { return observed || location != null; }
        public String status() { return observed ? "[x]" : location != null ? "[~]" : "[?]"; }
    }
}
