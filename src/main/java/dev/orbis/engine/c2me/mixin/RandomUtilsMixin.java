package dev.orbis.engine.c2me.mixin;

import dev.orbis.engine.noise.FoldedRandomFactory;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * C2ME reseeds positional randomness by reaching into vanilla's two factories and rejects any other. A torus world's
 * folded factory is unwrapped here: the coordinates are folded first, exactly as {@link FoldedRandomFactory#at} does.
 */
@Pseudo
@Mixin(targets = "com.ishland.c2me.opts.worldgen.general.common.random_instances.RandomUtils", remap = false)
public abstract class RandomUtilsMixin {
    @Shadow public static void derive(PositionalRandomFactory factory, RandomSource random, int x, int y, int z) { throw new AssertionError(); }
    @Shadow public static RandomSource getRandom(PositionalRandomFactory factory) { throw new AssertionError(); }
    @Shadow public static RandomSource getThreadLocalRandom(PositionalRandomFactory factory) { throw new AssertionError(); }

    @Inject(method = "derive", at = @At("HEAD"), cancellable = true)
    private static void orbis$deriveFolded(PositionalRandomFactory factory, RandomSource random, int x, int y, int z, CallbackInfo ci) {
        if (!(factory instanceof FoldedRandomFactory folded)) return;
        derive(folded.delegate(), random, Math.floorMod(x, folded.period()), y, Math.floorMod(z, folded.period()));
        ci.cancel();
    }

    @Inject(method = "getRandom", at = @At("HEAD"), cancellable = true)
    private static void orbis$randomFolded(PositionalRandomFactory factory, CallbackInfoReturnable<RandomSource> cir) {
        if (factory instanceof FoldedRandomFactory folded) cir.setReturnValue(getRandom(folded.delegate()));
    }

    @Inject(method = "getThreadLocalRandom", at = @At("HEAD"), cancellable = true)
    private static void orbis$threadRandomFolded(PositionalRandomFactory factory, CallbackInfoReturnable<RandomSource> cir) {
        if (factory instanceof FoldedRandomFactory folded) cir.setReturnValue(getThreadLocalRandom(folded.delegate()));
    }
}
