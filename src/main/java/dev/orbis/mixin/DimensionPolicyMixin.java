package dev.orbis.mixin;

import com.toroidalworld.core.ShapedChunkGenerator;
import com.toroidalworld.engine.gen.ShapedDimensions;
import dev.orbis.OrbisShape;
import net.minecraft.core.Registry;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Orbis declares only the three vanilla dimensions; other dimensions retain their own generator policy. */
@Mixin(ShapedDimensions.class)
public abstract class DimensionPolicyMixin {
    @Inject(method = "stampDerived", at = @At("HEAD"), cancellable = true)
    private static void orbis$keepOtherDimensions(Registry<LevelStem> dimensions, CallbackInfo callback) {
        var overworld = dimensions.getOptional(LevelStem.OVERWORLD).orElse(null);
        var carried = overworld == null ? null : ShapedChunkGenerator.carriedShapeOf(overworld.generator());
        if (carried != null && carried.generationOptions().get(OrbisShape.ENABLED)) {
            OrbisShape.validateRestored(dimensions);
            callback.cancel();
        }
    }
}
