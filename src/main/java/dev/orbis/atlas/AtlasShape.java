package dev.orbis.atlas;

import com.toroidalworld.api.v1.ToroidalWorldApi;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/** The map's frame for a level: the torus of a wrapped level, otherwise a 4096-block window around the origin. */
record AtlasShape(boolean wrapped, int minX, int minZ, int span) {
    static final AtlasShape OPEN = new AtlasShape(false, -2048, -2048, 4096);

    static AtlasShape of(@Nullable Torus torus) {
        return torus == null ? OPEN : new AtlasShape(true, torus.minBlock(), torus.minBlock(), torus.widthBlocks());
    }

    static AtlasShape server(ServerLevel level) {
        Torus torus = Tori.of(level);
        if (torus != null) return of(torus);
        // Worlds created on the old engine, until it is removed.
        var shape = ToroidalWorldApi.shapeOf(level).orElse(null);
        if (shape == null || !shape.loops(Direction.Axis.X) || !shape.loops(Direction.Axis.Z)) return OPEN;
        return new AtlasShape(true, shape.minBlock(Direction.Axis.X), shape.minBlock(Direction.Axis.Z),
                shape.widthBlocks(Direction.Axis.X));
    }

    int foldChunkX(int chunk) { return wrapped ? (minX >> 4) + Math.floorMod(chunk - (minX >> 4), span >> 4) : chunk; }

    int foldChunkZ(int chunk) { return wrapped ? (minZ >> 4) + Math.floorMod(chunk - (minZ >> 4), span >> 4) : chunk; }

    long foldChunk(long key) { return ChunkPos.pack(foldChunkX(ChunkPos.getX(key)), foldChunkZ(ChunkPos.getZ(key))); }

    /** The chunk image of {@code folded} nearest to {@code reference}, in the reference's frame. */
    int nearestChunk(int folded, int reference) {
        if (!wrapped) return folded;
        int width = span >> 4;
        int delta = Math.floorMod(folded - reference + width / 2, width) - width / 2;
        return reference + delta;
    }

    AtlasSnapshot.Cell fold(AtlasSnapshot.Cell cell) {
        int x = foldChunkX(cell.x()), z = foldChunkZ(cell.z());
        return x == cell.x() && z == cell.z() ? cell : new AtlasSnapshot.Cell(x, z, cell.color(), cell.copyTerrain());
    }
}
