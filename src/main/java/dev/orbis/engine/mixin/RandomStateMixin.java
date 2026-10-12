package dev.orbis.engine.mixin;

import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusHolder;
import dev.orbis.engine.noise.FoldedRandomFactory;
import dev.orbis.engine.noise.PeriodicNoises;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctionCompiler;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import net.minecraft.world.level.levelgen.synth.Noise;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A random state built for a torus level samples periodic density functions and surface noise. */
@Mixin(RandomState.class)
public abstract class RandomStateMixin implements TorusHolder {
    @Shadow @Final private DensityFunctionCompiler densityFunctionCompiler;
    @Shadow @Final private MaterialSystem materialSystem;
    @Unique private @Nullable Torus orbis$torus;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void orbis$bindTorus(CallbackInfo callback) {
        Torus torus = Tori.pending();
        if (torus == null) return;
        orbis$torus = torus;
        Tori.bind(((DensityFunctionCompilerAccessor) densityFunctionCompiler).orbis$context(), torus);
        var surface = (MaterialSystemAccessor) materialSystem;
        double w = torus.widthBlocks();
        surface.orbis$setClayBandsOffsetNoise(orbis$periodic(surface.orbis$clayBandsOffsetNoise(), w));
        surface.orbis$setSurfaceNoise(orbis$periodic(surface.orbis$surfaceNoise(), w));
        surface.orbis$setSurfaceSecondaryNoise(orbis$periodic(surface.orbis$surfaceSecondaryNoise(), w));
        surface.orbis$setBadlandsSurfaceNoise(orbis$periodic(surface.orbis$badlandsSurfaceNoise(), w));
        surface.orbis$setIcebergSurfaceNoise(orbis$periodic(surface.orbis$icebergSurfaceNoise(), w));
        // MaterialSystem samples these at fixed fractions of the block position.
        surface.orbis$setBadlandsPillarNoise(orbis$periodic(surface.orbis$badlandsPillarNoise(), w * 0.2));
        surface.orbis$setBadlandsPillarRoofNoise(orbis$periodic(surface.orbis$badlandsPillarRoofNoise(), w * 0.75));
        surface.orbis$setIcebergPillarNoise(orbis$periodic(surface.orbis$icebergPillarNoise(), w * 1.28));
        surface.orbis$setIcebergPillarRoofNoise(orbis$periodic(surface.orbis$icebergPillarRoofNoise(), w * 1.17));
        surface.orbis$setNoiseRandom(FoldedRandomFactory.of(surface.orbis$noiseRandom(), torus.widthBlocks()));
    }

    @Unique
    private static Noise orbis$periodic(Noise noise, double period) {
        return PeriodicNoises.periodic(noise, period, 0, period);
    }

    @Override public @Nullable Torus orbis$torus() { return orbis$torus; }

    @Override public void orbis$setTorus(@Nullable Torus torus) { orbis$torus = torus; }
}
