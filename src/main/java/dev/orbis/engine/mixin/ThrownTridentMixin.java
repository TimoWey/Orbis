package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A loyal trident flies home to the image of its owner nearest to it, over the seam rather than round the world. */
@Mixin(ThrownTrident.class)
public abstract class ThrownTridentMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 orbis$nearestOwner(Entity owner, Operation<Vec3> original) {
        Entity self = (Entity) (Object) this;
        return Fold.nearest(Tori.of(self.level()), original.call(owner), self.position());
    }
}
