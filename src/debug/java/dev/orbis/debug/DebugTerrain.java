package dev.orbis.debug;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** Reads only the supplied, already-loaded chunk, never requests neighbouring chunks. */
final class DebugTerrain {
    static DebugSnapshot.Cell sample(LevelChunk chunk, int fallbackColor) {
        var colors = new ArrayList<Integer>(256);
        var pos = new BlockPos.MutableBlockPos();
        int originX = chunk.getPos().x() * 16, originZ = chunk.getPos().z() * 16;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
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
            colors.add(color);
        }
        return new DebugSnapshot.Cell(chunk.getPos().x(), chunk.getPos().z(), fallbackColor, colors);
    }
    private DebugTerrain() {}
}
