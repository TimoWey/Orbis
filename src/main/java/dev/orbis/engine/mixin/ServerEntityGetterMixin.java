package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerEntityGetter;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A player is near a box when the player's image nearest to it is inside. */
@Mixin(ServerEntityGetter.class)
public interface ServerEntityGetterMixin {
    @WrapOperation(method = "getNearbyPlayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;contains(DDD)Z"))
    private static boolean orbis$nearestPlayer(AABB box, double x, double y, double z, Operation<Boolean> original,
                                               @Local Player player) {
        return original.call(Fold.nearest(Tori.of(player.level()), box, x, z), x, y, z);
    }
}
