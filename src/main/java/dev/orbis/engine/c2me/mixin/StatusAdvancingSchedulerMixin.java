package dev.orbis.engine.c2me.mixin;

import dev.orbis.engine.Fold;
import dev.orbis.engine.c2me.C2meTorus;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * C2ME schedules chunks in its own scheduler, keyed by chunk position, and asks it for every neighbour a chunk
 * depends on at the neighbour's plain position. On a torus each key is folded first, so a chunk past the seam is
 * the chunk at its place in the world, held once, exactly as Orbis folds vanilla's chunk holders. A chunk counts
 * the neighbours it waits on by key, so those keys are folded too: in a narrow world two neighbours on either side
 * can be the same chunk, and it is waited on once.
 */
@Pseudo
@Mixin(targets = "com.ishland.flowsched.scheduler.StatusAdvancingScheduler", remap = false)
public abstract class StatusAdvancingSchedulerMixin {
    @ModifyVariable(method = {"getHolder", "getOrCreateHolder", "createHolder0", "addTicket", "addTicket0",
            "removeTicket", "removeTicket0", "swapTicket"}, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Object orbis$foldKey(Object key) {
        if (key instanceof ChunkPos pos && (Object) this instanceof C2meTorus scheduler) {
            var torus = scheduler.orbis$torus();
            if (torus != null) return Fold.chunk(torus, pos);
        }
        return key;
    }

    @ModifyArg(method = "*", at = @At(value = "INVOKE", target = "Lcom/ishland/flowsched/scheduler/ItemHolder;addDependencyTicket(Lcom/ishland/flowsched/scheduler/StatusAdvancingScheduler;Ljava/lang/Object;Lcom/ishland/flowsched/scheduler/ItemStatus;Lcom/ishland/flowsched/scheduler/ItemTicket;)V"), index = 1)
    private Object orbis$foldDependency(Object key) { return orbis$foldKey(key); }

    @ModifyArg(method = "*", at = @At(value = "INVOKE", target = "Lcom/ishland/flowsched/scheduler/ItemHolder;removeDependencyTicket(Ljava/lang/Object;Lcom/ishland/flowsched/scheduler/ItemStatus;)V"), index = 0)
    private Object orbis$foldReleasedDependency(Object key) { return orbis$foldKey(key); }
}
