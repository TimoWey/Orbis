package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Sounds, particles and block events reach players near any image of their source. */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @WrapOperation(method = "broadcast", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getX()D"))
    private double orbis$nearestX(ServerPlayer player, Operation<Double> original, @Local(argsOnly = true, ordinal = 0) double x) {
        Torus torus = Tori.of(player.level());
        double px = original.call(player);
        return torus == null ? px : torus.nearest(px, x);
    }

    @WrapOperation(method = "broadcast", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getZ()D"))
    private double orbis$nearestZ(ServerPlayer player, Operation<Double> original, @Local(argsOnly = true, ordinal = 2) double z) {
        Torus torus = Tori.of(player.level());
        double pz = original.call(player);
        return torus == null ? pz : torus.nearest(pz, z);
    }
}
