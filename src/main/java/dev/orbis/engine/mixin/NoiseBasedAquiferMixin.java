package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.noise.FoldedRandomFactory;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fluid levels and lava are chosen by sampling noise at cell coordinates (16 and 64 blocks wide), where the
 * block-periodic noise does not repeat; folding the cell index makes the choice repeat with the world.
 * A torus aquifer is recognised by its folded randomness, whose period is the width in 16-block cells.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.Aquifer$NoiseBasedAquifer")
public abstract class NoiseBasedAquiferMixin {
    @Shadow @Final private PositionalRandomFactory positionalRandomFactory;

    @WrapOperation(method = "computeRandomizedFluidSurfaceLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/levelgen/densityfunction/DensitySampler$Bound;sampleValue(III)F"))
    private float orbis$foldSpreadCell(DensitySampler.Bound noise, int x, int y, int z, Operation<Float> original) {
        if (!(positionalRandomFactory instanceof FoldedRandomFactory folded)) return original.call(noise, x, y, z);
        int cells = folded.period();
        return original.call(noise, Math.floorMod(x, cells), y, Math.floorMod(z, cells));
    }

    @WrapOperation(method = "computeFluidType", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/levelgen/densityfunction/DensitySampler$Bound;sampleValue(III)F"))
    private float orbis$foldLavaCell(DensitySampler.Bound noise, int x, int y, int z, Operation<Float> original) {
        if (!(positionalRandomFactory instanceof FoldedRandomFactory folded)) return original.call(noise, x, y, z);
        int cells = folded.period() / 4;
        return original.call(noise, Math.floorMod(x, cells), y, Math.floorMod(z, cells));
    }
}
