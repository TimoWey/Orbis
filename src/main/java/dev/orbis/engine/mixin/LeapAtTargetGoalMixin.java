package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Fold;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** A spider or wolf leaps toward the image of its target nearest to it. */
@Mixin(LeapAtTargetGoal.class)
public abstract class LeapAtTargetGoalMixin {
    @Shadow @Final private Mob mob;

    @WrapOperation(method = "start", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D"))
    private double orbis$targetX(LivingEntity target, Operation<Double> original) {
        return Fold.nearX(mob, original.call(target));
    }

    @WrapOperation(method = "start", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D"))
    private double orbis$targetZ(LivingEntity target, Operation<Double> original) {
        return Fold.nearZ(mob, original.call(target));
    }
}
