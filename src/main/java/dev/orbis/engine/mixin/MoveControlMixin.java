package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.orbis.engine.Fold;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** A mob steers toward the image of a spot nearest to it, such as a target it flies at directly. */
@Mixin(MoveControl.class)
public abstract class MoveControlMixin {
    @Shadow @Final protected Mob mob;

    @WrapMethod(method = "setWantedPosition(DDDD)V")
    private void orbis$wantNearest(double x, double y, double z, double speed, Operation<Void> original) {
        original.call(Fold.nearX(mob, x), y, Fold.nearZ(mob, z), speed);
    }
}
