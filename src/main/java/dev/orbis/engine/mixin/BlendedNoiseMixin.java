package dev.orbis.engine.mixin;

import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import dev.orbis.engine.noise.PeriodicNoises;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.generator.NoiseFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.ClampFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.LerpFunction;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.Noise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The old 3D terrain noise, assembled exactly as vanilla does but from periodic octaves. */
@Mixin(BlendedNoise.class)
public abstract class BlendedNoiseMixin {
    @Inject(method = "compileSampler(Lnet/minecraft/world/level/levelgen/densityfunction/DensityFunction$CompileContext;)"
            + "Lnet/minecraft/world/level/levelgen/densityfunction/DensitySampler;", at = @At("HEAD"), cancellable = true)
    private void orbis$periodic(DensityFunction.CompileContext context, CallbackInfoReturnable<DensitySampler> callback) {
        Torus torus = Tori.of(context);
        if (torus == null) return;
        var self = (BlendedNoise) (Object) this;
        var fbms = self.createFbmSet(context.createRandom(BlendedNoise.NOISE_SEED));
        double xz = self.xzMultiplier(), y = self.yMultiplier(), w = torus.widthBlocks();
        double mainXz = xz / self.xzFactor();
        Noise min = PeriodicNoises.periodic(fbms.minLimitNoise(), w * xz, 0, w * xz);
        Noise max = PeriodicNoises.periodic(fbms.maxLimitNoise(), w * xz, 0, w * xz);
        Noise main = PeriodicNoises.periodic(fbms.mainNoise(), w * mainXz, 0, w * mainXz);
        DensitySampler choice = new ClampFunction.Sampler(new BinaryFunction.ConstAddSampler(
                new NoiseFunction.Sampler(main, mainXz, y / self.yFactor()), 0.5F), 0.0F, 1.0F);
        callback.setReturnValue(new LerpFunction.Sampler(choice,
                new NoiseFunction.Sampler(min, xz, y), new NoiseFunction.Sampler(max, xz, y)));
    }
}
