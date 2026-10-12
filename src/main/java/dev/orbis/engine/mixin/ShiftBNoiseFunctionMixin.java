package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.noise.PeriodicNoises;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.generator.ShiftNoiseFunction;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShiftNoiseFunction.ShiftB.class)
public abstract class ShiftBNoiseFunctionMixin {
    @WrapOperation(method = "compileSampler", at = @At(value = "INVOKE", target = Targets.CREATE_NOISE))
    private Noise orbis$periodic(DensityFunction.CompileContext context, Holder<NormalNoise> holder, Operation<Noise> original) {
        return PeriodicNoises.forShift(context, original.call(context, holder), true);
    }
}
