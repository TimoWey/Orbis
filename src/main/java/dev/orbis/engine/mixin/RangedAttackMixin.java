package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Fold;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Drowned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A ranged mob aims at the image of its target nearest to it, so the shot crosses the seam instead of turning away. */
@Mixin({AbstractSkeleton.class, SnowGolem.class, Illusioner.class, Drowned.class, Witch.class})
public abstract class RangedAttackMixin {
    private static final String ATTACK = "performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V";

    @WrapOperation(method = ATTACK, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D"))
    private double orbis$targetX(LivingEntity target, Operation<Double> original) {
        return Fold.nearX((Entity) (Object) this, original.call(target));
    }

    @WrapOperation(method = ATTACK, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D"))
    private double orbis$targetZ(LivingEntity target, Operation<Double> original) {
        return Fold.nearZ((Entity) (Object) this, original.call(target));
    }
}
