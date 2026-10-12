package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin {
    @ModifyVariable(method = "getBlockEntityNbt", at = @At("HEAD"), argsOnly = true)
    private BlockPos orbis$inChunk(BlockPos pos) { return Fold.inChunk(pos, ((ChunkAccess) (Object) this).getPos()); }
}
