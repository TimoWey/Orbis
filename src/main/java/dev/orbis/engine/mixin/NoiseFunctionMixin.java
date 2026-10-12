package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import dev.orbis.engine.noise.PeriodicNoises;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.generator.NoiseFunction;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Noise samples (x * xzScale, y * yScale, z * xzScale), so its X and Z inputs repeat every width * xzScale. */
@Mixin(NoiseFunction.class)
public abstract class NoiseFunctionMixin {
    @Shadow @Final private double xzScale;

    @WrapOperation(method = "compileSampler", at = @At(value = "INVOKE", target = Targets.CREATE_NOISE))
    private Noise orbis$periodic(DensityFunction.CompileContext context, Holder<NormalNoise> holder, Operation<Noise> original) {
        Noise noise = original.call(context, holder);
        Torus torus = Tori.of(context);
        if (torus == null) return noise;
        double period = torus.widthBlocks() * xzScale;
        return PeriodicNoises.periodic(noise, period, 0, period);
    }
}
