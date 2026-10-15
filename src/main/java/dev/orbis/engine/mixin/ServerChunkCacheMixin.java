package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every chunk exists once: requests for any image of a chunk are answered by its folded position. */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {
    @Shadow @Final private ServerLevel level;

    @Unique private Torus orbis$torus() { return Tori.of(level); }

    // getChunkFutureMainThread too: pregenerators such as Chunky call it directly, also for the row just past the
    // world. Unfolded, that request found no chunk, finished at once and dropped the shared folded ticket while the
    // real chunk was still generating, which left it half done.
    @ModifyVariable(method = {"getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            "getChunkNow", "getChunkFuture", "getChunkFutureMainThread", "hasChunk", "getChunkForLighting"},
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int orbis$foldX(int x) { return Fold.chunkCoord(orbis$torus(), x); }

    @ModifyVariable(method = {"getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            "getChunkNow", "getChunkFuture", "getChunkFutureMainThread", "hasChunk", "getChunkForLighting"},
            at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int orbis$foldZ(int z) { return Fold.chunkCoord(orbis$torus(), z); }

    @ModifyVariable(method = "blockChanged", at = @At("HEAD"), argsOnly = true)
    private BlockPos orbis$foldBlock(BlockPos pos) { return Fold.block(orbis$torus(), pos); }

    @ModifyVariable(method = "onLightUpdate", at = @At("HEAD"), argsOnly = true)
    private SectionPos orbis$foldSection(SectionPos pos) { return Fold.section(orbis$torus(), pos); }

    @ModifyVariable(method = {"addTicket", "addTicketAndLoadWithRadius", "addTicketWithRadius", "removeTicketWithRadius",
            "updateChunkForced"}, at = @At("HEAD"), argsOnly = true)
    private ChunkPos orbis$foldTicket(ChunkPos pos) { return Fold.chunk(orbis$torus(), pos); }

    @ModifyVariable(method = "isPositionTicking", at = @At("HEAD"), argsOnly = true)
    private long orbis$foldTicking(long key) { return Fold.chunk(orbis$torus(), key); }
}
