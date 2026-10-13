package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
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
}
