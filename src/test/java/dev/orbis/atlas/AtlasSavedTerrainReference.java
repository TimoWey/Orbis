package dev.orbis.atlas;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.material.*;

/** Alpha.10 reference implementation retained only for pixel and timing comparisons. */
final class AtlasSavedTerrainReference {
    static AtlasSnapshot.Cell sample(ChunkPos position, LevelHeightAccessor height, boolean ceiling,
            List<SerializableChunkData.SectionData> sections) {
        var blocks = new SavedBlocks(height, sections);
        int[] colors = new int[256];
        var pos = new BlockPos.MutableBlockPos();
        int slice = AtlasTerrain.sliceY(height.getMinY(), height.getMaxY());
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int color = AtlasTerrain.EMPTY_SLICE_COLOR;
            int y = ceiling ? slice : height.getMaxY();
            int remaining = ceiling ? 1 : Integer.MAX_VALUE;
            for (; y >= height.getMinY() && remaining > 0; y--) {
                if (!ceiling && remaining == Integer.MAX_VALUE) {
                    var section = blocks.sections().get(y >> 4);
                    if (section == null || section.hasOnlyAir()) { y = (y >> 4) * 16; continue; }
                }
                pos.set(position.getMinBlockX() + x, y, position.getMinBlockZ() + z);
                var state = blocks.getBlockState(pos);
                if (!ceiling && remaining == Integer.MAX_VALUE) {
                    if (state.isAir()) continue;
                    remaining = 16;
                }
                remaining--;
                MapColor map = state.getMapColor(blocks, pos);
                if (map != MapColor.NONE) { color = 0xff000000 | map.col; break; }
            }
            colors[z * 16 + x] = color;
        }
        return new AtlasSnapshot.Cell(position.x(), position.z(), AtlasTerrain.EMPTY_SLICE_COLOR, colors);
    }

    private record SavedBlocks(LevelHeightAccessor height, Map<Integer, LevelChunkSection> sections) implements BlockGetter {
        SavedBlocks(LevelHeightAccessor height, List<SerializableChunkData.SectionData> data) {
            this(height, new HashMap<>());
            for (var section : data) if (section.chunkSection() != null) sections.put(section.y(), section.chunkSection());
        }
        public BlockState getBlockState(BlockPos pos) {
            var section = sections.get(pos.getY() >> 4);
            return section == null ? Blocks.AIR.defaultBlockState() : section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        }
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        public int getMinY() { return height.getMinY(); }
        public int getHeight() { return height.getHeight(); }
    }

}
