package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** On a torus the distance between two things is the distance to the nearest image, the way round that is shorter. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @WrapMethod(method = "distanceToSqr(DDD)D")
    private double orbis$distanceToSqr(double x, double y, double z, Operation<Double> original) {
        Torus torus = orbis$torus();
        return torus == null ? original.call(x, y, z) : orbis$sqr(torus, x, y, z);
    }

    @WrapMethod(method = "distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D")
    private double orbis$distanceToSqrVec(Vec3 pos, Operation<Double> original) {
        Torus torus = orbis$torus();
        return torus == null ? original.call(pos) : orbis$sqr(torus, pos.x, pos.y, pos.z);
    }

    @WrapMethod(method = "distanceToSqr(Lnet/minecraft/world/entity/Entity;)D")
    private double orbis$distanceToSqrEntity(Entity other, Operation<Double> original) {
        Torus torus = orbis$torus();
        return torus == null ? original.call(other) : orbis$sqr(torus, other.getX(), other.getY(), other.getZ());
    }

    @WrapMethod(method = "distanceTo")
    private float orbis$distanceTo(Entity other, Operation<Float> original) {
        Torus torus = orbis$torus();
        return torus == null ? original.call(other) : (float) Math.sqrt(orbis$sqr(torus, other.getX(), other.getY(), other.getZ()));
    }

    @WrapMethod(method = "closerThan(Lnet/minecraft/world/entity/Entity;DD)Z")
    private boolean orbis$closerThan(Entity other, double horizontal, double vertical, Operation<Boolean> original) {
        Torus torus = orbis$torus();
        if (torus == null) return original.call(other, horizontal, vertical);
        Entity self = (Entity) (Object) this;
        double dx = torus.delta(self.getX(), other.getX()), dy = other.getY() - self.getY(), dz = torus.delta(self.getZ(), other.getZ());
        return dx * dx + dz * dz < horizontal * horizontal && dy * dy < vertical * vertical;
    }

    /** Two entities touching across the seam push each other apart, not along the long way round. */
    @WrapOperation(method = "push(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D"))
    private double orbis$pushX(Entity entity, Operation<Double> original) {
        return Fold.nearX((Entity) (Object) this, original.call(entity));
    }

    @WrapOperation(method = "push(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D"))
    private double orbis$pushZ(Entity entity, Operation<Double> original) {
        return Fold.nearZ((Entity) (Object) this, original.call(entity));
    }

    @ModifyVariable(method = "lookAt(Lnet/minecraft/commands/arguments/EntityAnchorArgument$Anchor;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"), argsOnly = true)
    private Vec3 orbis$lookAtNearest(Vec3 pos) {
        Entity self = (Entity) (Object) this;
        return Fold.nearest(orbis$torus(), pos, self.position());
    }

    @Unique
    private Torus orbis$torus() { return Tori.of(((Entity) (Object) this).level()); }

    @Unique
    private double orbis$sqr(Torus torus, double x, double y, double z) {
        Entity self = (Entity) (Object) this;
        double dx = torus.delta(self.getX(), x), dy = y - self.getY(), dz = torus.delta(self.getZ(), z);
        return dx * dx + dy * dy + dz * dz;
    }
}
