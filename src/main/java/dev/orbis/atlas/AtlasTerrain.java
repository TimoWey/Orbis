package dev.orbis.atlas;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** Reads only the supplied, already-loaded chunk, never requests neighbouring chunks. */
final class AtlasTerrain {
    static final int CEILING_SLICE_Y = 32;
    static final int EMPTY_SLICE_COLOR = 0xff283039;
    static AtlasSnapshot.Cell sample(LevelChunk chunk, int fallbackColor) {
        boolean ceiling = chunk.getLevel().dimensionType().hasCeiling();
        var colors = new int[256];
        var pos = new BlockPos.MutableBlockPos();
        int originX = chunk.getPos().x() * 16, originZ = chunk.getPos().z() * 16;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            if (ceiling) {
                // A fixed horizontal slice avoids the Nether roof. Air is neutral;
                // observed blocks retain their actual map colors without biome tint.
                int y = sliceY(chunk.getMinY(), chunk.getMaxY());
                pos.set(originX + x, y, originZ + z);
                var map = chunk.getBlockState(pos).getMapColor(chunk, pos);
                colors[z * 16 + x] = sliceColor(map.col, map != MapColor.NONE);
                continue;
            }
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            int color = fallbackColor;
            // Skip transparent surface blocks with a strict per-column bound.
            for (int depth = 0; depth < 16 && y >= chunk.getMinY(); depth++, y--) {
                pos.set(originX + x, y, originZ + z);
                var state = chunk.getBlockState(pos);
                var map = state.getMapColor(chunk, pos);
                if (map != MapColor.NONE) {
                    color = 0xff000000 | map.col;
                    break;
                }
            }
            colors[z * 16 + x] = color;
        }
        return new AtlasSnapshot.Cell(chunk.getPos().x(), chunk.getPos().z(), fallbackColor, colors);
    }
    static int sliceY(int minY, int maxY) { return Math.clamp(CEILING_SLICE_Y, minY, maxY); }
    static int sliceColor(int block, boolean present) {
        return present ? 0xff000000 | block : EMPTY_SLICE_COLOR;
    }
    private AtlasTerrain() {}
}
