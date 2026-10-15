package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** A mob looks at the image of a spot nearest to it. */
@Mixin(LookControl.class)
public abstract class LookControlMixin {
    @Shadow @Final protected Mob mob;

    @WrapMethod(method = "setLookAt(DDDFF)V")
    private void orbis$lookAtNearest(double x, double y, double z, float yawSpeed, float pitchSpeed, Operation<Void> original) {
        Torus torus = Tori.of(mob.level());
        if (torus != null) {
            x = torus.nearest(x, mob.getX());
            z = torus.nearest(z, mob.getZ());
        }
        original.call(x, y, z, yawSpeed, pitchSpeed);
    }
}
