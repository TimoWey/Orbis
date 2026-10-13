package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.Fold;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.breeze.Shoot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A breeze shoots its wind charge at the image of its target nearest to it. */
@Mixin(Shoot.class)
public abstract class BreezeShootMixin {
    private static final String TICK = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/breeze/Breeze;J)V";

    @WrapOperation(method = TICK, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D"))
    private double orbis$targetX(LivingEntity target, Operation<Double> original, @Local(argsOnly = true) Breeze breeze) {
        return Fold.nearX(breeze, original.call(target));
    }

    @WrapOperation(method = TICK, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D"))
    private double orbis$targetZ(LivingEntity target, Operation<Double> original, @Local(argsOnly = true) Breeze breeze) {
        return Fold.nearZ(breeze, original.call(target));
    }
}
