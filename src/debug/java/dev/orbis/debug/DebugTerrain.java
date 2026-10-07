package dev.orbis.debug;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** Reads only the supplied, already-loaded chunk, never requests neighbouring chunks. */
final class DebugTerrain {
    static final int CEILING_SLICE_Y = 32;
    static DebugSnapshot.Cell sample(LevelChunk chunk, int fallbackColor) {
        boolean ceiling = chunk.getLevel().dimensionType().hasCeiling();
        var colors = new ArrayList<Integer>(256);
        var pos = new BlockPos.MutableBlockPos();
        int originX = chunk.getPos().x() * 16, originZ = chunk.getPos().z() * 16;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            if (ceiling) {
                // The Nether roof is not a useful map. Read an actual horizontal slice
                // instead; biome tint keeps netherrack/lava expanses distinguishable.
                int y = sliceY(chunk.getMinY(), chunk.getMaxY());
                pos.set(originX + x, y, originZ + z);
                String biome = chunk.getNoiseBiome(pos.getX() >> 2, y >> 2, pos.getZ() >> 2).unwrapKey()
                        .map(key -> key.identifier().toString()).orElse("unregistered");
                var map = chunk.getBlockState(pos).getMapColor(chunk, pos);
                colors.add(sliceColor(DebugMapMath.biomeColor(biome), map == MapColor.NONE ? null : map.col));
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
            colors.add(color);
        }
        return new DebugSnapshot.Cell(chunk.getPos().x(), chunk.getPos().z(), fallbackColor, colors);
    }
    static int sliceY(int minY, int maxY) { return Math.clamp(CEILING_SLICE_Y, minY, maxY); }
    static int sliceColor(int biome, Integer block) {
        if (block == null) return biome;
        return 0xff000000 | (((biome >> 16 & 255) * 3 + (block >> 16 & 255)) / 4 << 16)
                | (((biome >> 8 & 255) * 3 + (block >> 8 & 255)) / 4 << 8)
                | ((biome & 255) * 3 + (block & 255)) / 4;
    }
    private DebugTerrain() {}
}
