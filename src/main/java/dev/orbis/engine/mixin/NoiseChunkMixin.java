package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusHolder;
import dev.orbis.engine.noise.FoldedRandomFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Aquifer centres are drawn per 16x12x16 grid cell; whole-chunk widths keep that grid aligned with the seam. */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/levelgen/RandomState;getOrCreateRandomFactory(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;"))
    private PositionalRandomFactory orbis$foldedAquifers(RandomState state, Identifier name, Operation<PositionalRandomFactory> original) {
        PositionalRandomFactory factory = original.call(state, name);
        Torus torus = ((TorusHolder) (Object) state).orbis$torus();
        return torus == null ? factory : FoldedRandomFactory.of(factory, torus.widthChunks());
    }
}
