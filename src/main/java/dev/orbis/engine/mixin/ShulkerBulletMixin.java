package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Fold;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A shulker bullet steers toward the image of its target nearest to it. */
@Mixin(ShulkerBullet.class)
public abstract class ShulkerBulletMixin {
    @WrapOperation(method = "selectNextMoveDirection", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D"))
    private double orbis$targetX(Entity target, Operation<Double> original) {
        return Fold.nearX((ShulkerBullet) (Object) this, original.call(target));
    }

    @WrapOperation(method = "selectNextMoveDirection", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D"))
    private double orbis$targetZ(Entity target, Operation<Double> original) {
        return Fold.nearZ((ShulkerBullet) (Object) this, original.call(target));
    }
}
