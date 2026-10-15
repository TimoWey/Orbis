package dev.orbis.engine.dh;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import dev.orbis.engine.dh.mixin.FullDataSourceV2Accessor;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.function.LongFunction;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Distant Horizons on a torus. DH keeps its far terrain in square sections at absolute positions; the client walks on
 * past the seam in its own frame, so DH asks for sections that lie one or more world widths away. Every such section
 * is an image of one inside the world: it is read from there, written there and generated there, and when that one
 * changes, its images in view are rebuilt too. Sections wider than the world's alignment allows (only in very small
 * worlds) are put together from their quarters.
 */
public final class DhTorus {
    /** DH downsamples no higher; coarser sections are not put together either. */
    private static final byte HIGHEST_COMPOSED_DETAIL = 12;

    private DhTorus() {}

    public static @Nullable Torus of(@Nullable ILevelWrapper wrapper) {
        if (wrapper == null) return null;
        Object world = wrapper.getWrappedMcObject();
        if (!(world instanceof Level level)) return null;
        Torus torus = Tori.of(level);
        if (torus == null && level.isClientSide() && FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT)
            torus = DhTorusClient.of(level);
        return torus;
    }

    static int width(long pos) { return DhSectionPos.getBlockWidth(pos); }

    /** Whether moving the section by whole world widths lands on another section. */
    public static boolean translatable(Torus torus, long pos) { return torus.widthBlocks() % width(pos) == 0; }

    /** Whether the section's image inside the world is a whole section of its own. */
    public static boolean relocatable(Torus torus, long pos) {
        int width = width(pos);
        return width <= torus.widthBlocks() && torus.widthBlocks() % width == 0 && Math.floorMod(torus.minBlock(), width) == 0;
    }

    public static long translate(long pos, int dxBlocks, int dzBlocks) {
        int width = width(pos);
        return DhSectionPos.encode(DhSectionPos.getDetailLevel(pos), DhSectionPos.getX(pos) + dxBlocks / width,
                DhSectionPos.getZ(pos) + dzBlocks / width);
    }

    /** The image inside the world of a relocatable section. */
    public static long fold(Torus torus, long pos) {
        int x = DhSectionPos.getMinCornerBlockX(pos), z = DhSectionPos.getMinCornerBlockZ(pos);
        return translate(pos, torus.fold(x) - x, torus.fold(z) - z);
    }

    /** A relocatable section outside the world, or {@code pos} itself. */
    public static long home(Torus torus, long pos) { return relocatable(torus, pos) ? fold(torus, pos) : pos; }

    public static void move(FullDataSourceV2 data, long pos) { ((FullDataSourceV2Accessor) data).orbis$setPos(pos); }

    /** Reads a section the way the world has it: from its image inside the world, or from its quarters. */
    public static @Nullable FullDataSourceV2 read(Torus torus, long pos, LongFunction<@Nullable FullDataSourceV2> stored) {
        if (relocatable(torus, pos)) {
            long home = fold(torus, pos);
            FullDataSourceV2 data = stored.apply(home);
            if (data != null && home != pos) move(data, pos);
            return data;
        }
        if (DhSectionPos.getDetailLevel(pos) > HIGHEST_COMPOSED_DETAIL) return stored.apply(pos);
        FullDataSourceV2 whole = FullDataSourceV2.createEmpty(pos);
        for (int child = 0; child < 4; child++) {
            FullDataSourceV2 quarter = read(torus, DhSectionPos.getChildByIndex(pos, child), stored);
            if (quarter == null) continue;
            try { whole.updateFromDataSource(quarter); }
            finally { quarter.close(); }
        }
        return whole;
    }

    /** Positions DH should generate for a section: those of its image inside the world, moved back next to it. */
    public static @Nullable LongArrayList missing(Torus torus, long pos, byte detail, java.util.function.BiFunction<Long, Byte, @Nullable LongArrayList> query) {
        long home = home(torus, pos);
        if (home != pos) return moved(query.apply(home, detail), pos, home);
        LongArrayList found = query.apply(pos, detail);
        if (found == null) return null;
        // A section across the edge lists parts beyond it, which are missing only if their images are.
        LongArrayList result = new LongArrayList();
        for (int i = 0; i < found.size(); i++) {
            long part = found.getLong(i), partHome = home(torus, part);
            if (partHome == part) { result.add(part); continue; }
            LongArrayList partMissing = moved(query.apply(partHome, DhSectionPos.getDetailLevel(part)), part, partHome);
            if (partMissing != null) result.addAll(partMissing);
        }
        return result;
    }

    private static @Nullable LongArrayList moved(@Nullable LongArrayList positions, long to, long from) {
        if (positions == null) return null;
        int dx = DhSectionPos.getMinCornerBlockX(to) - DhSectionPos.getMinCornerBlockX(from);
        int dz = DhSectionPos.getMinCornerBlockZ(to) - DhSectionPos.getMinCornerBlockZ(from);
        LongArrayList result = new LongArrayList(positions.size());
        for (int i = 0; i < positions.size(); i++) result.add(translate(positions.getLong(i), dx, dz));
        return result;
    }
}
