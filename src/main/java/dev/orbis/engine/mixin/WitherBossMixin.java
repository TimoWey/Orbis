package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Fold;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The wither flies toward, turns its heads to and shoots at the images of its targets nearest to it. */
@Mixin(WitherBoss.class)
public abstract class WitherBossMixin {
    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D"))
    private double orbis$targetX(Entity target, Operation<Double> original) {
        return Fold.nearX((WitherBoss) (Object) this, original.call(target));
    }

    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D"))
    private double orbis$targetZ(Entity target, Operation<Double> original) {
        return Fold.nearZ((WitherBoss) (Object) this, original.call(target));
    }

    @WrapOperation(method = "performRangedAttack(ILnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D"))
    private double orbis$shotX(LivingEntity target, Operation<Double> original) {
        return Fold.nearX((WitherBoss) (Object) this, original.call(target));
    }

    @WrapOperation(method = "performRangedAttack(ILnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D"))
    private double orbis$shotZ(LivingEntity target, Operation<Double> original) {
        return Fold.nearZ((WitherBoss) (Object) this, original.call(target));
    }
}
