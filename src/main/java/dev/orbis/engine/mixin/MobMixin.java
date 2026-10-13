package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A mob turns toward, and hits, the image of its target that is nearest to it. */
@Mixin(Mob.class)
public abstract class MobMixin {
    @ModifyVariable(method = "lookAt(Lnet/minecraft/world/entity/Entity;FF)V", at = @At("STORE"), ordinal = 0)
    private double orbis$lookDeltaX(double dx, @Local(argsOnly = true) Entity target) {
        Torus torus = Tori.of(target.level());
        return torus == null ? dx : torus.delta(((Mob) (Object) this).getX(), target.getX());
    }

    @ModifyVariable(method = "lookAt(Lnet/minecraft/world/entity/Entity;FF)V", at = @At("STORE"), ordinal = 1)
    private double orbis$lookDeltaZ(double dz, @Local(argsOnly = true) Entity target) {
        Torus torus = Tori.of(target.level());
        return torus == null ? dz : torus.delta(((Mob) (Object) this).getZ(), target.getZ());
    }

    @ModifyExpressionValue(method = "isWithinMeleeAttackRange",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getHitbox()Lnet/minecraft/world/phys/AABB;"))
    private AABB orbis$nearestHitbox(AABB hitbox) {
        Mob self = (Mob) (Object) this;
        return Fold.nearest(Tori.of(self.level()), hitbox, self.getX(), self.getZ());
    }
}
