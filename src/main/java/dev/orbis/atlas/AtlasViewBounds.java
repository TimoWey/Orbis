package dev.orbis.atlas;

import java.util.List;

/** Fits the known part of an unwrapped dimension without imposing a world boundary. */
record AtlasViewBounds(double centerX, double centerZ, double width, double depth) {
    static AtlasViewBounds known(List<AtlasSnapshot.Cell> chunks,
            int playerX, int playerZ) {
        double minX = playerX, maxX = playerX, minZ = playerZ, maxZ = playerZ;
        for (var chunk : chunks) {
            double x = (double) chunk.x() * 16, z = (double) chunk.z() * 16;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x + 16);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z + 16);
        }
        return new AtlasViewBounds((minX + maxX) / 2, (minZ + maxZ) / 2,
                Math.max(128, maxX - minX + 64), Math.max(128, maxZ - minZ + 64));
    }
    double zoom(int referenceSpan, int viewportWidth, int viewportHeight) {
        double pixelsPerBlock = Math.min(viewportWidth / width, viewportHeight / depth);
        return pixelsPerBlock * referenceSpan / Math.max(16, Math.min(viewportWidth, viewportHeight));
    }
}
