package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Scheduled ticks belong to the chunk that holds the folded block. */
@Mixin(LevelTicks.class)
public abstract class LevelTicksMixin<T> implements TorusHolder {
    @Unique private @Nullable Torus orbis$torus;

    @ModifyVariable(method = "schedule", at = @At("HEAD"), argsOnly = true)
    private ScheduledTick<T> orbis$foldTick(ScheduledTick<T> tick) {
        BlockPos folded = Fold.block(orbis$torus, tick.pos());
        return folded == tick.pos() ? tick : new ScheduledTick<>(tick.type(), folded, tick.triggerTick(), tick.priority(), tick.subTickOrder());
    }

    @ModifyVariable(method = {"hasScheduledTick", "willTickThisTick"}, at = @At("HEAD"), argsOnly = true)
    private BlockPos orbis$foldQuery(BlockPos pos) { return Fold.block(orbis$torus, pos); }

    @Override public @Nullable Torus orbis$torus() { return orbis$torus; }

    @Override public void orbis$setTorus(@Nullable Torus torus) { orbis$torus = torus; }
}
