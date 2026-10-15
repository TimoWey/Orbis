package dev.orbis.engine;

import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;

/**
 * A structure start keeps its pieces in the frame of the chunk it started in. Generation near the seam needs the
 * structure where it reaches the chunk being generated, which can be one width away: that image is a copy of the
 * start with every piece (and every jigsaw junction the terrain adapts to) moved by whole widths.
 */
public final class StructureFrames {
    /** Mixed into {@link StructureStart}: its copies by shift in blocks, packed as one long. */
    public interface Holder {
        Map<Long, StructureStart> orbis$images();
    }

    private StructureFrames() {}

    public static StructureStart toward(Torus torus, ServerLevel level, StructureStart start, ChunkPos chunk) {
        if (!start.isValid()) return start;
        ChunkPos home = start.getChunkPos();
        int dx = (torus.nearestChunk(home.x(), chunk.x()) - home.x()) * 16;
        int dz = (torus.nearestChunk(home.z(), chunk.z()) - home.z()) * 16;
        if (dx == 0 && dz == 0) return start;
        return ((Holder) (Object) start).orbis$images().computeIfAbsent(ChunkPos.pack(dx, dz), key -> moved(level, start, dx, dz));
    }

    private static StructureStart moved(ServerLevel level, StructureStart start, int dx, int dz) {
        var context = StructurePieceSerializationContext.fromLevel(level);
        StructureStart copy = StructureStart.loadStaticStart(context, start.createTag(context, start.getChunkPos()), level.getSeed());
        if (copy == null) return start;
        copy.getPieces().forEach(piece -> {
            piece.move(dx, 0, dz);
            if (piece instanceof PoolElementStructurePiece pool) {
                pool.getJunctions().replaceAll(junction -> new JigsawJunction(junction.getSourceX() + dx,
                        junction.getSourceGroundY(), junction.getSourceZ() + dz, junction.getDeltaY(), junction.getDestProjection()));
            }
        });
        return copy;
    }
}
