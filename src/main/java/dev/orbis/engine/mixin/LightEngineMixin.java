package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import dev.orbis.engine.LightTorus;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Light is stored once per section; reads of any image use the folded block. Only the server's level has a torus. */
@Mixin(LightEngine.class)
public abstract class LightEngineMixin implements LightTorus {
    @Shadow @Final protected LightChunkGetter chunkSource;

    @Override public @Nullable Torus orbis$torus() {
        return chunkSource.getLevel() instanceof Level level ? Tori.of(level) : null;
    }

    @ModifyVariable(method = "getLightValue(Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), argsOnly = true)
    private BlockPos orbis$fold(BlockPos pos) { return Fold.block(orbis$torus(), pos); }
}
