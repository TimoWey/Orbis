package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A blast reaches entities past the seam the short way: it throws them away from it, and their exposure is traced
 * from the image of the blast next to them. Blocks need nothing; their rays read and break through the folded level.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
    @Shadow @Final private ServerLevel level;

    @WrapOperation(method = "hurtEntities",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 orbis$awayFromBlast(Vec3 entity, Vec3 center, Operation<Vec3> original) {
        return original.call(Fold.nearest(Tori.of(level), entity, center), center);
    }

    @WrapOperation(method = "hurtEntities",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerExplosion;getSeenPercent(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;)F"))
    private float orbis$exposure(Vec3 center, Entity entity, Operation<Float> original) {
        return original.call(Fold.nearest(Tori.of(level), center, entity.position()), entity);
    }
}
