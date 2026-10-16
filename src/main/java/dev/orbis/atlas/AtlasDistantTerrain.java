package dev.orbis.atlas;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.file.fullDatafile.IDataSourceUpdateListenerFunc;
import com.seibel.distanthorizons.core.file.fullDatafile.V2.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/** Optional DH 3.3 adapter. Reads saved leaf data, never requests DH world generation. */
final class AtlasDistantTerrain implements AutoCloseable {
    private final FullDataSourceProviderV2 provider;
    private final int minY, maxY;
    private final boolean ceiling;
    private final Set<Long> changed = new LinkedHashSet<>();
    private final IDataSourceUpdateListenerFunc<FullDataSourceV2> listener = source -> {
        if (source.getDataDetailLevel() != 0) return;
        synchronized (changed) {
            // Overflow is recovered by the next enumeration, without retaining DH buffers.
            if (changed.size() < 8192) changed.add(source.getPos());
        }
    };
    private Iterator<Long> existing = Collections.emptyIterator();
    private final Map<Long, Long> imported = new HashMap<>();

    static AtlasDistantTerrain find(ServerLevel level) {
        if (DhApi.Delayed.worldProxy == null || !DhApi.Delayed.worldProxy.worldLoaded()) return null;
        for (var candidate : DhApi.Delayed.worldProxy.getAllLoadedLevelWrappers()) {
            if (!(candidate instanceof ILevelWrapper wrapper) || wrapper.getDhLevel() == null) continue;
            Object world = candidate.getWrappedMcObject();
            if (world != level && !(world instanceof Level client && client.isClientSide() && client.dimension().equals(level.dimension()))) continue;
            return new AtlasDistantTerrain(wrapper.getDhLevel().getFullDataProvider(), level.getMinY(), level.getMaxY(),
                    level.dimensionType().hasCeiling());
        }
        return null;
    }

    private AtlasDistantTerrain(FullDataSourceProviderV2 provider, int minY, int maxY, boolean ceiling) {
        this.provider = provider; this.minY = minY; this.maxY = maxY; this.ceiling = ceiling;
        provider.addDataSourceUpdateListener(listener);
    }

    void startScan() { existing = provider.repo.getAllPositions().iterator(); }

    boolean readNext(Consumer<AtlasSnapshot.Cell> accept) {
        Long position = null;
        synchronized (changed) {
            var it = changed.iterator();
            if (it.hasNext()) { position = it.next(); it.remove(); }
        }
        boolean notified = position != null;
        while (position == null && existing.hasNext()) {
            long next = existing.next();
            if (DhSectionPos.getDetailLevel(next) == FullDataSourceProviderV2.LEAF_SECTION_DETAIL_LEVEL) position = next;
        }
        if (position == null) return false;
        Long timestamp = provider.repo.getTimestampForPos(position);
        if (!notified && timestamp != null && timestamp.equals(imported.get(position))) return true;
        try (var source = provider.get(position)) {
            if (source == null || source.isEmpty || source.getDataDetailLevel() != 0) return true;
            sample(source, minY, maxY, ceiling, accept);
            if (timestamp != null) imported.put(position, timestamp);
        }
        return true;
    }

    static void sample(FullDataSourceV2 source, int minY, int maxY, boolean ceiling, Consumer<AtlasSnapshot.Cell> accept) {
        if (source.getDataDetailLevel() != 0) return;
        int originX = DhSectionPos.getX(source.getPos()) * FullDataSourceV2.WIDTH;
        int originZ = DhSectionPos.getZ(source.getPos()) * FullDataSourceV2.WIDTH;
        var surfaces = new HashMap<Integer, Surface>();
        for (int cz = 0; cz < FullDataSourceV2.WIDTH; cz += 16) for (int cx = 0; cx < FullDataSourceV2.WIDTH; cx += 16) {
            int[] colors = new int[256], heights = new int[256], depths = new int[256];
            Arrays.fill(heights, AtlasShading.FLAT);
            boolean complete = true;
            for (int z = 0; z < 16 && complete; z++) for (int x = 0; x < 16; x++) {
                var column = source.tryGetColumnAtRelPos(cx + x, cz + z);
                if (column == null || column.isEmpty() || source.columnGenerationSteps.getByte(
                        FullDataSourceV2.relativePosToIndex(cx + x, cz + z)) < EDhApiWorldGenerationStep.SURFACE.value) {
                    complete = false; break;
                }
                int color = AtlasTerrain.EMPTY_SLICE_COLOR;
                int slice = AtlasTerrain.sliceY(minY, maxY);
                for (int i = 0; i < column.size(); i++) {
                    long point = column.getLong(i);
                    int bottom = minY + FullDataPointUtil.getBottomY(point);
                    int top = bottom + FullDataPointUtil.getHeight(point);
                    if (ceiling && (slice < bottom || slice >= top)) continue;
                    var surface = surfaces.computeIfAbsent(FullDataPointUtil.getId(point), key -> Surface.of(source, key));
                    if (surface.color != 0) {
                        color = surface.color;
                        heights[z * 16 + x] = top - 1;
                        // Merged runs: the water point and any fluid points beneath it give the depth.
                        if (surface.water) for (int j = i, depth = 0; j < column.size(); j++) {
                            long below = column.getLong(j);
                            if (!surfaces.computeIfAbsent(FullDataPointUtil.getId(below), key -> Surface.of(source, key)).fluid) break;
                            depth += FullDataPointUtil.getHeight(below);
                            depths[z * 16 + x] = Math.min(depth, AtlasShading.WATER_DEPTH_LIMIT);
                            if (depth >= AtlasShading.WATER_DEPTH_LIMIT) break;
                        }
                        break;
                    }
                    if (ceiling) break;
                }
                colors[z * 16 + x] = color;
            }
            if (complete && !ceiling) AtlasShading.shadeTile(colors, heights, depths, originX + cx, originZ + cz);
            if (complete) accept.accept(new AtlasSnapshot.Cell((originX + cx) >> 4, (originZ + cz) >> 4,
                    AtlasTerrain.EMPTY_SLICE_COLOR, colors));
        }

    }

    private record Surface(int color, boolean water, boolean fluid) {
        static Surface of(FullDataSourceV2 source, int id) {
            Object block = source.mapping.getBlockStateWrapper(id).getWrappedMcObject();
            if (!(block instanceof BlockState state)) return new Surface(0, false, false);
            MapColor map = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            return new Surface(map == MapColor.NONE ? 0 : 0xff000000 | map.col, map == MapColor.WATER,
                    !state.getFluidState().isEmpty());
        }
    }

    public void close() { provider.removeDataSourceUpdateListener(listener); synchronized (changed) { changed.clear(); } }
}
