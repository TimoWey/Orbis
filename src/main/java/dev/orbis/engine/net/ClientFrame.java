package dev.orbis.engine.net;

import dev.orbis.engine.Fold;
import dev.orbis.engine.Torus;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
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

    /** Beyond the farthest view distance a teleport keeps none of the client's chunks, so its frame may start afresh. */
    private static final int FAR = 512;

    private @Nullable Level level;
    private volatile int lapX, lapZ;
    private @Nullable Vec3 teleportFrom;
    private volatile boolean jumped;
    /** Folded chunk to the client-frame position it was last sent at, so it is forgotten exactly there. */
    private final Long2LongMap sentAt = Long2LongMaps.synchronize(new Long2LongOpenHashMap());

    /** A new level starts a new frame, just like the client starts a new level. */
    private void follow(ServerPlayer player) {
        Level current = player.level();
        if (current != level) {
            level = current;
            lapX = 0;
            lapZ = 0;
            sentAt.clear();
            teleportFrom = null;
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

    /** Where the server is about to teleport the player from, in the same level. */
    public void teleporting(ServerPlayer player) {
        // Changing dimension, the player is already in the new level but still at the old position, which means
        // nothing there: the new frame starts at the arrival.
        boolean sameLevel = player.level() == level;
        follow(player);
        teleportFrom = sameLevel ? player.position() : null;
    }

    /**
     * Absolute positions the server puts the player at go to the image nearest to where the client is, so a short
     * teleport over the seam is a short move for the client too. A far one starts the frame afresh instead, so the
     * client lands at exactly the coordinates the command named. Either way the lap may change, which moves every
     * chunk the client holds; {@link #takeJump()} reports that once.
     */
    public Vec3 teleported(@Nullable Torus torus, ServerPlayer player, Vec3 at, boolean relativeX, boolean relativeZ) {
        follow(player);
        Vec3 from = teleportFrom;
        teleportFrom = null;
        if (torus != null && from != null) {
            double nearX = relativeX ? from.x : torus.nearest(at.x, from.x);
            double nearZ = relativeZ ? from.z : torus.nearest(at.z, from.z);
            int newLapX = lapX, newLapZ = lapZ;
            if (Math.abs(nearX - from.x) > FAR || Math.abs(nearZ - from.z) > FAR) {
                if (!relativeX) newLapX = 0;
                if (!relativeZ) newLapZ = 0;
            } else {
                if (!relativeX) newLapX += (int) Math.round(nearX - at.x);
                if (!relativeZ) newLapZ += (int) Math.round(nearZ - at.z);
            }
            if (newLapX != lapX || newLapZ != lapZ) {
                lapX = newLapX;
                lapZ = newLapZ;
                jumped = true;
            }
        }
        return new Vec3(relativeX ? at.x : at.x + lapX, at.y, relativeZ ? at.z : at.z + lapZ);
    }

    /** Whether the client jumped a whole width since the last call: its chunks must all be sent again. */
    public boolean takeJump() {
        boolean was = jumped;
        jumped = false;
        return was;
    }

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

    /**
     * Where a chunk goes in the client frame, and where the client still holds it from an earlier send, if that is
     * elsewhere. In a world narrower than the view the nearest image of a chunk changes while it stays in view.
     */
    public long sendChunk(Torus torus, ServerPlayer player, ChunkPos folded, long[] previous) {
        long image = chunk(torus, player, folded).pack();
        long key = folded.pack();
        boolean held = sentAt.containsKey(key);
        long old = sentAt.put(key, image);
        previous[0] = held && old != image ? old : Long.MIN_VALUE;
        return image;
    }

    /** The client-frame position a forgotten chunk was sent at, or its nearest image if it never was. */
    public ChunkPos forgetChunk(Torus torus, ServerPlayer player, ChunkPos folded) {
        follow(player);
        long key = folded.pack();
        if (sentAt.containsKey(key)) return ChunkPos.unpack(sentAt.remove(key));
        return chunk(torus, player, folded);
    }

    public SectionPos section(Torus torus, ServerPlayer player, SectionPos pos) {
        return SectionPos.of(chunkX(torus, player, pos.x()), pos.y(), chunkZ(torus, player, pos.z()));
    }
}
