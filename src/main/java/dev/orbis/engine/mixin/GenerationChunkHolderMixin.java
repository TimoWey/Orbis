package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.Tori;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GeneratingChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The old engine refuses to step a neighbour across the seam inside another chunk's generation and queues it for
 * its own levels only; on an Orbis-engine level that neighbour would then never be generated, and a chunk loaded
 * on the server thread waits for it forever. Here the step goes ahead: {@code ChunkMapMixin} re-indexes the
 * neighbour cache around it. Applied after the old engine's wrapper, so this one runs first.
 */
@Mixin(value = GenerationChunkHolder.class, priority = 2000)
public abstract class GenerationChunkHolderMixin {
    @Invoker("acquireStatusBump") abstract boolean orbis$acquireStatusBump(ChunkStatus status);

    @WrapOperation(method = "applyStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/GenerationChunkHolder;acquireStatusBump(Lnet/minecraft/world/level/chunk/status/ChunkStatus;)Z"))
    private boolean orbis$stepAcrossTheSeam(GenerationChunkHolder holder, ChunkStatus status, Operation<Boolean> original,
                                            @Local(argsOnly = true) GeneratingChunkMap chunkMap) {
        if (chunkMap instanceof ChunkMap map && Tori.of(((ChunkMapAccessor) map).orbis$level()) != null) {
            return ((GenerationChunkHolderMixin) (Object) holder).orbis$acquireStatusBump(status);
        }
        return original.call(holder, status);
    }
}
