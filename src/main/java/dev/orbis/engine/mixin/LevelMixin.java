package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Level.class)
public abstract class LevelMixin implements TorusHolder {
    @Unique private @Nullable Torus orbis$torus;

    /**
     * A block set at any image is set at the folded position, so everything that follows (neighbour updates,
     * block entities, points of interest, game events) sees one position per block.
     */
    @ModifyVariable(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), argsOnly = true)
    private BlockPos orbis$foldBlock(BlockPos pos) { return Fold.block(orbis$torus, pos); }

    @Override public @Nullable Torus orbis$torus() { return orbis$torus; }

    @Override public void orbis$setTorus(@Nullable Torus torus) { orbis$torus = torus; }
}
