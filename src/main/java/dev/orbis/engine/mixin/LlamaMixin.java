package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Fold;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.Llama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A llama spits at the image of its target nearest to it. */
@Mixin(Llama.class)
public abstract class LlamaMixin {
    @WrapOperation(method = "spit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D"))
    private double orbis$targetX(LivingEntity target, Operation<Double> original) {
        return Fold.nearX((Llama) (Object) this, original.call(target));
    }

    @WrapOperation(method = "spit", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D"))
    private double orbis$targetZ(LivingEntity target, Operation<Double> original) {
        return Fold.nearZ((Llama) (Object) this, original.call(target));
    }
}
