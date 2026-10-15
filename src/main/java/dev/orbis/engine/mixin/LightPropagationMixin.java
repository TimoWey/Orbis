package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Fold;
import dev.orbis.engine.LightTorus;
import net.minecraft.core.Direction;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Light spreading over the seam continues in the folded block, so it reaches the other side. */
@Mixin({BlockLightEngine.class, SkyLightEngine.class})
public abstract class LightPropagationMixin {
    @WrapOperation(method = {"propagateIncrease", "propagateDecrease"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;offset(JLnet/minecraft/core/Direction;)J"))
    private long orbis$foldStep(long node, Direction direction, Operation<Long> original) {
        return Fold.block(((LightTorus) this).orbis$torus(), (long) original.call(node, direction));
    }
}
