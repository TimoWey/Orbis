package dev.orbis.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import java.util.function.Consumer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Folding of Minecraft position types onto the torus; every method returns its input when there is no torus. */
public final class Fold {
    private Fold() {}

    public static BlockPos block(@Nullable Torus torus, BlockPos pos) {
        if (torus == null || torus.contains(pos.getX()) && torus.contains(pos.getZ())) return pos;
        return new BlockPos(torus.fold(pos.getX()), pos.getY(), torus.fold(pos.getZ()));
    }

    public static long block(@Nullable Torus torus, long packed) {
        if (torus == null) return packed;
        int x = BlockPos.getX(packed), z = BlockPos.getZ(packed);
        if (torus.contains(x) && torus.contains(z)) return packed;
        return BlockPos.asLong(torus.fold(x), BlockPos.getY(packed), torus.fold(z));
    }

    public static ChunkPos chunk(@Nullable Torus torus, ChunkPos pos) {
        if (torus == null || torus.containsChunk(pos.x()) && torus.containsChunk(pos.z())) return pos;
        return new ChunkPos(torus.foldChunk(pos.x()), torus.foldChunk(pos.z()));
    }

    public static long chunk(@Nullable Torus torus, long packed) {
        if (torus == null) return packed;
        int x = ChunkPos.getX(packed), z = ChunkPos.getZ(packed);
        if (torus.containsChunk(x) && torus.containsChunk(z)) return packed;
        return ChunkPos.pack(torus.foldChunk(x), torus.foldChunk(z));
    }

    public static int chunkCoord(@Nullable Torus torus, int chunk) { return torus == null ? chunk : torus.foldChunk(chunk); }

    public static SectionPos section(@Nullable Torus torus, SectionPos pos) {
        if (torus == null || torus.containsChunk(pos.x()) && torus.containsChunk(pos.z())) return pos;
        return SectionPos.of(torus.foldChunk(pos.x()), pos.y(), torus.foldChunk(pos.z()));
    }

    public static long section(@Nullable Torus torus, long packed) {
        if (torus == null) return packed;
        int x = SectionPos.x(packed), z = SectionPos.z(packed);
        if (torus.containsChunk(x) && torus.containsChunk(z)) return packed;
        return SectionPos.asLong(torus.foldChunk(x), SectionPos.y(packed), torus.foldChunk(z));
    }

    /** The image of {@code pos} nearest to {@code reference}. */
    public static Vec3 nearest(@Nullable Torus torus, Vec3 pos, Vec3 reference) {
        if (torus == null) return pos;
        double x = torus.nearest(pos.x, reference.x), z = torus.nearest(pos.z, reference.z);
        return x == pos.x && z == pos.z ? pos : new Vec3(x, pos.y, z);
    }

    public static BlockPos nearest(@Nullable Torus torus, BlockPos pos, double referenceX, double referenceZ) {
        if (torus == null) return pos;
        int x = (int) Math.round(torus.nearest(pos.getX() + 0.5, referenceX) - 0.5);
        int z = (int) Math.round(torus.nearest(pos.getZ() + 0.5, referenceZ) - 0.5);
        return x == pos.getX() && z == pos.getZ() ? pos : new BlockPos(x, pos.getY(), z);
    }

    /** The same column inside {@code chunk}, for chunk-local storage that is keyed by world position. */
    public static BlockPos inChunk(BlockPos pos, ChunkPos chunk) {
        if (SectionPos.blockToSectionCoord(pos.getX()) == chunk.x() && SectionPos.blockToSectionCoord(pos.getZ()) == chunk.z()) return pos;
        return new BlockPos(chunk.getBlockX(pos.getX() & 15), pos.getY(), chunk.getBlockZ(pos.getZ() & 15));
    }

    /** {@code box} moved by whole widths so that its centre is the image nearest to the reference. */
    public static AABB nearest(@Nullable Torus torus, AABB box, double referenceX, double referenceZ) {
        if (torus == null) return box;
        double centreX = (box.minX + box.maxX) / 2, centreZ = (box.minZ + box.maxZ) / 2;
        double dx = torus.nearest(centreX, referenceX) - centreX, dz = torus.nearest(centreZ, referenceZ) - centreZ;
        return dx == 0 && dz == 0 ? box : box.move(dx, 0, dz);
    }

    /**
     * The parts of {@code box} that cover the torus, each moved by whole widths so it overlaps the folded world.
     * A box at least as wide as the torus is clipped to it instead. Without a torus the box itself is the one part.
     */
    public static void pieces(@Nullable Torus torus, AABB box, Consumer<AABB> action) {
        if (torus == null) {
            action.accept(box);
            return;
        }
        double[] xs = shifts(torus, box.minX, box.maxX), zs = shifts(torus, box.minZ, box.maxZ);
        for (double x : xs) {
            for (double z : zs) {
                double minX = Double.isNaN(x) ? torus.minBlock() : box.minX + x, maxX = Double.isNaN(x) ? torus.minBlock() + torus.widthBlocks() : box.maxX + x;
                double minZ = Double.isNaN(z) ? torus.minBlock() : box.minZ + z, maxZ = Double.isNaN(z) ? torus.minBlock() + torus.widthBlocks() : box.maxZ + z;
                action.accept(new AABB(minX, box.minY, minZ, maxX, box.maxY, maxZ));
            }
        }
    }

    /** The whole-width shifts that bring [min, max] onto the torus on one axis; NaN means clip to the torus. */
    private static double[] shifts(Torus torus, double min, double max) {
        int width = torus.widthBlocks();
        if (max - min >= width) return new double[] {Double.NaN};
        long first = (long) Math.floor((min - torus.minBlock()) / width), last = (long) Math.floor((max - torus.minBlock()) / width);
        double[] shifts = new double[(int) (last - first + 1)];
        for (int i = 0; i < shifts.length; i++) shifts[i] = -(first + i) * (double) width;
        return shifts;
    }
}
