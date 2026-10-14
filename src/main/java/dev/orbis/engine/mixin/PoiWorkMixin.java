package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.SleepInBed;
import net.minecraft.world.entity.ai.behavior.WorkAtPoi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A villager working at or going to sleep in a point just past the seam is next to it, measured the short way. */
@Mixin({SleepInBed.class, WorkAtPoi.class})
public abstract class PoiWorkMixin {
    @WrapOperation(method = {"checkExtraStartConditions", "canStillUse"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"))
    private boolean orbis$nearPoi(BlockPos pos, Position at, double distance, Operation<Boolean> original, @Local(argsOnly = true) ServerLevel level) {
        return original.call(Fold.nearest(Tori.of(level), pos, at.x(), at.z()), at, distance);
    }
}
