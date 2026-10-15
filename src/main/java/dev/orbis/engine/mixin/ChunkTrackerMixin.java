package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusHolder;
import net.minecraft.server.level.ChunkTracker;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Ticket levels spread to folded neighbours, so chunks across the seam load and no duplicate holder appears. */
@Mixin(ChunkTracker.class)
public abstract class ChunkTrackerMixin implements TorusHolder {
    @Unique private @Nullable Torus orbis$torus;

    @WrapOperation(method = {"checkNeighborsAfterUpdate", "getComputedLevel"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/ChunkPos;pack(II)J"))
    private long orbis$foldNeighbour(int x, int z, Operation<Long> original) {
        return Fold.chunk(orbis$torus, (long) original.call(x, z));
    }

    @Override public @Nullable Torus orbis$torus() { return orbis$torus; }

    @Override public void orbis$setTorus(@Nullable Torus torus) { orbis$torus = torus; }
}
