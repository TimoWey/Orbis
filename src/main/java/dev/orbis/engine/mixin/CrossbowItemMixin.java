package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.Fold;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A pillager or piglin aims its crossbow at the image of its target nearest to it. */
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {
    @WrapOperation(method = "shootProjectile", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D"))
    private double orbis$targetX(LivingEntity entity, Operation<Double> original, @Local(argsOnly = true, ordinal = 0) LivingEntity shooter) {
        return Fold.nearX(shooter, original.call(entity));
    }

    @WrapOperation(method = "shootProjectile", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D"))
    private double orbis$targetZ(LivingEntity entity, Operation<Double> original, @Local(argsOnly = true, ordinal = 0) LivingEntity shooter) {
        return Fold.nearZ(shooter, original.call(entity));
    }
}
