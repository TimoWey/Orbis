package dev.orbis.mixin;

import dev.orbis.OrbisShape;

import com.toroidalworld.shape.torus.TorusSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pinned compatibility patch: upstream's generic torus reader otherwise claims
 * every two-axis world before third-party shapes can restore their settings. */
@Mixin(targets = "com.toroidalworld.shape.torus.TorusDimensions")
public abstract class TorusOwnershipMixin {
    @Inject(method = "read", at = @At("HEAD"), cancellable = true)
    private static void orbis$keepOwnership(WorldDimensions dimensions,
            CallbackInfoReturnable<TorusSettings> callback) {
        if (OrbisShape.owns(dimensions)) callback.setReturnValue(null);
    }
}
