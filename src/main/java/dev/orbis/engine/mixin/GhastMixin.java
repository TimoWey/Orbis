package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.Fold;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ghast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A ghast faces the image of its target nearest to it. */
@Mixin(Ghast.class)
public abstract class GhastMixin {
    @WrapOperation(method = "faceMovementDirection", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D"))
    private static double orbis$targetX(LivingEntity target, Operation<Double> original, @Local(argsOnly = true) Mob ghast) {
        return Fold.nearX(ghast, original.call(target));
    }

    @WrapOperation(method = "faceMovementDirection", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D"))
    private static double orbis$targetZ(LivingEntity target, Operation<Double> original, @Local(argsOnly = true) Mob ghast) {
        return Fold.nearZ(ghast, original.call(target));
    }
}
