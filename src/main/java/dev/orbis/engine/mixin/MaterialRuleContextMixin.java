package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusHolder;
import dev.orbis.engine.noise.PeriodicNoises;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.orbis.engine.noise.FoldedRandomFactory;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Surface-rule noise thresholds sample block coordinates directly. */
@Mixin(MaterialRuleContext.class)
public abstract class MaterialRuleContextMixin {
    @Shadow @Final private RandomState randomState;

    /** Vertical gradients (bedrock) and ore veins draw randomness per block. */
    @ModifyReturnValue(method = "getOrCreateRandomFactory", at = @At("RETURN"))
    private PositionalRandomFactory orbis$folded(PositionalRandomFactory factory) {
        Torus torus = ((TorusHolder) (Object) randomState).orbis$torus();
        return torus == null ? factory : FoldedRandomFactory.of(factory, torus.widthBlocks());
    }

    @WrapOperation(method = {"createNoiseSampler2d", "createNoiseSampler3d"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/levelgen/RandomState;getOrCreateNoise(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/level/levelgen/synth/Noise;"))
    private Noise orbis$periodic(RandomState state, ResourceKey<NormalNoise> key, Operation<Noise> original) {
        Noise noise = original.call(state, key);
        Torus torus = ((TorusHolder) (Object) state).orbis$torus();
        return torus == null ? noise : PeriodicNoises.periodic(noise, torus.widthBlocks(), 0, torus.widthBlocks());
    }
}
