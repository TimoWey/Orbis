package dev.orbis.atlas;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

/** Detached NBT only. Decode block palettes lazily; no biome/light/entity decoding. */
final class AtlasSavedSurface implements BlockGetter {
    private final LevelHeightAccessor height;
    private final PalettedContainerFactory factory;
    private final Map<Integer, CompoundTag> encoded = new HashMap<>();
    private final Map<Integer, PalettedContainer<BlockState>> decoded = new HashMap<>();
    private final Set<Integer> empty = new HashSet<>();
    private final SimpleBitStorage surface;

    AtlasSavedSurface(LevelHeightAccessor height, PalettedContainerFactory factory, CompoundTag tag) {
        this.height = height; this.factory = factory;
        for (var entry : tag.getListOrEmpty("sections")) entry.asCompound().ifPresent(section -> {
            int y = section.getByteOr("Y", (byte) 0);
            if (y >= height.getMinSectionY() && y <= height.getMaxSectionY())
                section.getCompound("block_states").ifPresent(states -> encoded.put(y, states));
        });
        SimpleBitStorage heights = null;
        var raw = tag.getCompoundOrEmpty("Heightmaps").getLongArray("WORLD_SURFACE").orElse(null);
        if (raw != null) try {
            heights = new SimpleBitStorage(Mth.ceillog2(height.getHeight() + 1), 256, raw);
            for (int i = 0; i < 256; i++) if (heights.get(i) > height.getHeight()) { heights = null; break; }
        } catch (SimpleBitStorage.InitializationException | IllegalArgumentException ignored) {
            // Missing/bad heightmaps use a bounded section scan.
        }
        surface = heights;
    }

    AtlasSnapshot.Cell sample(ChunkPos position, boolean ceiling) {
        var pos = new BlockPos.MutableBlockPos();
        int[] colors = new int[256], heights = new int[256], depths = new int[256];
        Arrays.fill(heights, AtlasShading.FLAT);
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int color = AtlasTerrain.EMPTY_SLICE_COLOR;
            // Saved heights store first air above the top non-air block, relative to minY.
            int y = ceiling ? AtlasTerrain.sliceY(getMinY(), height.getMaxY())
                    : surface == null ? height.getMaxY() : getMinY() + surface.get(z * 16 + x) - 1;
            int remaining = ceiling ? 1 : Integer.MAX_VALUE;
            for (; y >= getMinY() && remaining > 0; y--) {
                if (!ceiling && remaining == Integer.MAX_VALUE && section(y >> 4) == null) {
                    y = (y >> 4) * 16; continue;
                }
                pos.set(position.getMinBlockX() + x, y, position.getMinBlockZ() + z);
                var state = getBlockState(pos);
                if (!ceiling && remaining == Integer.MAX_VALUE) {
                    if (state.isAir()) continue;
                    remaining = 16;
                }
                remaining--;
                var map = state.getMapColor(this, pos);
                if (map != MapColor.NONE) {
                    color = 0xff000000 | map.col;
                    heights[z * 16 + x] = y;
                    if (map == MapColor.WATER) depths[z * 16 + x] = AtlasTerrain.waterDepth(this, pos, getMinY());
                    break;
                }
            }
            colors[z * 16 + x] = color;
        }
        if (!ceiling) AtlasShading.shadeTile(colors, heights, depths, position.getMinBlockX(), position.getMinBlockZ());
        return new AtlasSnapshot.Cell(position.x(), position.z(), AtlasTerrain.EMPTY_SLICE_COLOR, colors);
    }

    private PalettedContainer<BlockState> section(int y) {
        if (empty.contains(y)) return null;
        var existing = decoded.get(y);
        if (existing != null) return existing;
        var tag = encoded.get(y);
        if (tag == null) { empty.add(y); return null; }
        var blocks = factory.blockStatesContainerCodec().parse(NbtOps.INSTANCE, tag).getOrThrow();
        if (!blocks.maybeHas(state -> !state.isAir())) { empty.add(y); return null; }
        decoded.put(y, blocks);
        return blocks;
    }
    public BlockState getBlockState(BlockPos pos) {
        var blocks = section(pos.getY() >> 4);
        return blocks == null ? Blocks.AIR.defaultBlockState() : blocks.get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }
    public BlockEntity getBlockEntity(BlockPos pos) { return null; }
    public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    public int getMinY() { return height.getMinY(); }
    public int getHeight() { return height.getHeight(); }
}
