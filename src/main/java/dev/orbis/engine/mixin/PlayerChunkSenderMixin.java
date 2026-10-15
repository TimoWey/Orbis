package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import java.util.Comparator;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Nearest chunks first, measured across the seam. */
@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderMixin {
    @ModifyArg(method = "collectChunksToSend", at = @At(value = "INVOKE", target =
            "Lcom/google/common/collect/Comparators;least(ILjava/util/Comparator;)Ljava/util/stream/Collector;"), index = 1)
    private Comparator<Long> orbis$nearestPending(Comparator<Long> original, @Local(argsOnly = true) ChunkMap chunkMap,
                                                  @Local(argsOnly = true) ChunkPos player) {
        Torus torus = Tori.of(((ChunkMapAccessor) chunkMap).orbis$level());
        return torus == null ? original : Comparator.comparingLong(pos -> orbis$distance(torus, player, ChunkPos.getX(pos), ChunkPos.getZ(pos)));
    }

    @ModifyArg(method = "collectChunksToSend", at = @At(value = "INVOKE", target =
            "Ljava/util/stream/Stream;sorted(Ljava/util/Comparator;)Ljava/util/stream/Stream;"), index = 0)
    private Comparator<LevelChunk> orbis$nearestLoaded(Comparator<LevelChunk> original, @Local(argsOnly = true) ChunkMap chunkMap,
                                                       @Local(argsOnly = true) ChunkPos player) {
        Torus torus = Tori.of(((ChunkMapAccessor) chunkMap).orbis$level());
        return torus == null ? original : Comparator.comparingLong(chunk -> orbis$distance(torus, player, chunk.getPos().x(), chunk.getPos().z()));
    }

    @Unique
    private static long orbis$distance(Torus torus, ChunkPos player, int x, int z) {
        long dx = torus.deltaChunks(player.x(), x), dz = torus.deltaChunks(player.z(), z);
        return dx * dx + dz * dz;
    }
}
