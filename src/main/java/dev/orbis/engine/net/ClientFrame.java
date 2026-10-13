package dev.orbis.engine.net;

import dev.orbis.engine.Fold;
import dev.orbis.engine.Torus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The coordinates one client sees. The client never jumps at the seam: it keeps walking in one continuous
 * frame, which is the server's folded frame shifted by whole world widths (the lap). Everything the server
 * sends is moved to its image nearest to the player, then into that frame, so the client sees the world
 * continue across every edge without knowing about the torus.
 */
public final class ClientFrame {
    /** Mixed into the server's game packet listener: one frame per connection. */
    public interface Holder {
        ClientFrame orbis$frame();
    }

    private @Nullable Level level;
    private volatile int lapX, lapZ;

    /** A new level starts a new frame, just like the client starts a new level. */
    private void follow(ServerPlayer player) {
        Level current = player.level();
        if (current != level) {
            level = current;
            lapX = 0;
            lapZ = 0;
        }
    }

    /** The server moved the player by whole widths; the client did not move, so the lap takes the difference. */
    public void playerShifted(ServerPlayer player, int dx, int dz) {
        follow(player);
        lapX -= dx;
        lapZ -= dz;
    }

    public double x(Torus torus, ServerPlayer player, double x) {
        follow(player);
        return torus.nearest(x, player.getX()) + lapX;
    }

    public double z(Torus torus, ServerPlayer player, double z) {
        follow(player);
        return torus.nearest(z, player.getZ()) + lapZ;
    }

    public Vec3 position(Torus torus, ServerPlayer player, Vec3 pos) {
        return new Vec3(x(torus, player, pos.x), pos.y, z(torus, player, pos.z));
    }

    /** Absolute positions the server puts the player at keep the player's own lap, wherever they are. */
    public double ownX(ServerPlayer player, double x) { follow(player); return x + lapX; }

    public double ownZ(ServerPlayer player, double z) { follow(player); return z + lapZ; }

    public BlockPos block(Torus torus, ServerPlayer player, BlockPos pos) {
        follow(player);
        return Fold.nearest(torus, pos, player.getX(), player.getZ()).offset(lapX, 0, lapZ);
    }

    public int chunkX(Torus torus, ServerPlayer player, int x) {
        follow(player);
        return torus.nearestChunk(x, player.chunkPosition().x()) + (lapX >> 4);
    }

    public int chunkZ(Torus torus, ServerPlayer player, int z) {
        follow(player);
        return torus.nearestChunk(z, player.chunkPosition().z()) + (lapZ >> 4);
    }

    public ChunkPos chunk(Torus torus, ServerPlayer player, ChunkPos pos) {
        return new ChunkPos(chunkX(torus, player, pos.x()), chunkZ(torus, player, pos.z()));
    }

    public SectionPos section(Torus torus, ServerPlayer player, SectionPos pos) {
        return SectionPos.of(chunkX(torus, player, pos.x()), pos.y(), chunkZ(torus, player, pos.z()));
    }
}
