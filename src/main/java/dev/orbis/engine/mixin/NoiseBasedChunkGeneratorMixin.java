package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.TorusChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A cave or canyon is seeded by the chunk it starts in. A chunk near the seam carves the starts of its neighbours
 * past the seam, so those are seeded by their folded position: the same cave then reaches both sides.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    @WrapOperation(method = "generateCarvers", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"))
    private void orbis$foldCarverSeed(WorldgenRandom random, long seed, int chunkX, int chunkZ, Operation<Void> original) {
        if ((Object) this instanceof TorusChunkGenerator generator) {
            chunkX = generator.torus().foldChunk(chunkX);
            chunkZ = generator.torus().foldChunk(chunkZ);
        }
        original.call(random, seed, chunkX, chunkZ);
    }
}
