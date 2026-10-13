package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Sight runs to the nearest image of the other entity; the ray reads blocks through the folding chunk lookup. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    /** The second point built in the method is the other entity's eye height at its own position. */
    @ModifyVariable(method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;D)Z",
            at = @At("STORE"), ordinal = 1)
    private Vec3 orbis$nearestEyes(Vec3 other) {
        LivingEntity self = (LivingEntity) (Object) this;
        return Fold.nearest(Tori.of(self.level()), other, self.position());
    }

    /**
     * Knockback comes as a direction from one entity to another. Across the seam that difference spans nearly the
     * whole width, pointing the long way round; the shortest difference points the way the blow came from.
     */
    @WrapMethod(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V")
    private void orbis$knockbackShortWay(double power, double xd, double zd, DamageSource source, float damage,
                                         boolean fromEffect, Operation<Void> original) {
        Torus torus = Tori.of(((LivingEntity) (Object) this).level());
        if (torus != null) {
            xd = torus.delta(0, xd);
            zd = torus.delta(0, zd);
        }
        original.call(power, xd, zd, source, damage, fromEffect);
    }

    @WrapMethod(method = "indicateDamage(DD)V")
    private void orbis$damageFromShortWay(double xd, double zd, Operation<Void> original) {
        Torus torus = Tori.of(((LivingEntity) (Object) this).level());
        if (torus != null) {
            xd = torus.delta(0, xd);
            zd = torus.delta(0, zd);
        }
        original.call(xd, zd);
    }
}
