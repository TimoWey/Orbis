package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Fold;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * An experience orb flies toward the image of its player nearest to it. Aimed at the far image, across a whole
 * world width, its next move swept every block on the way for collisions and stalled the server for minutes.
 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {
    @WrapOperation(method = "followNearbyPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getX()D"))
    private double orbis$playerX(Player player, Operation<Double> original) {
        return Fold.nearX((ExperienceOrb) (Object) this, original.call(player));
    }

    @WrapOperation(method = "followNearbyPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getZ()D"))
    private double orbis$playerZ(Player player, Operation<Double> original) {
        return Fold.nearZ((ExperienceOrb) (Object) this, original.call(player));
    }
}
