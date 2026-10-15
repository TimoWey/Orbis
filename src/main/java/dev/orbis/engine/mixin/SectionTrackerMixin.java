package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Fold;
import dev.orbis.engine.TorusHolder;
import net.minecraft.server.level.SectionTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Section distances (how far a section is from a village) spread to the neighbours past the seam: a neighbour's
 * key is its folded section, so a village on one side reaches the ground on the other.
 */
@Mixin(SectionTracker.class)
public abstract class SectionTrackerMixin {
    @WrapOperation(method = {"checkNeighborsAfterUpdate", "getComputedLevel"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;offset(JIII)J"))
    private long orbis$foldNeighbour(long node, int dx, int dy, int dz, Operation<Long> original) {
        long neighbour = original.call(node, dx, dy, dz);
        return (Object) this instanceof TorusHolder holder ? Fold.section(holder.orbis$torus(), neighbour) : neighbour;
    }
}
