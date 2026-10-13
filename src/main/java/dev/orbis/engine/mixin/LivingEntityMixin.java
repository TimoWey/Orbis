package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Sight runs to the nearest image of the other entity; the ray reads blocks through the folding chunk lookup. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @WrapOperation(method = "hasLineOfSight", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 orbis$nearestEyes(Entity entity, Operation<Vec3> original) {
        Vec3 eyes = original.call(entity);
        LivingEntity self = (LivingEntity) (Object) this;
        return entity == self ? eyes : Fold.nearest(Tori.of(self.level()), eyes, self.position());
    }
}
