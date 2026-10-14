package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** World generation next to the seam writes through unfolded positions; see {@link LevelChunkMixin}. */
@Mixin(ProtoChunk.class)
public abstract class ProtoChunkMixin {
    @ModifyVariable(method = {"setBlockState", "getBlockEntity", "getBlockEntityNbtForSaving", "removeBlockEntity"},
            at = @At("HEAD"), argsOnly = true)
    private BlockPos orbis$inChunk(BlockPos pos) { return Fold.inChunk(pos, ((ChunkAccess) (Object) this).getPos()); }

    @ModifyVariable(method = "setBlockEntity", at = @At("HEAD"), argsOnly = true)
    private BlockEntity orbis$entityInChunk(BlockEntity entity) {
        BlockPos pos = Fold.inChunk(entity.getBlockPos(), ((ChunkAccess) (Object) this).getPos());
        if (pos != entity.getBlockPos()) ((BlockEntityAccessor) entity).orbis$setWorldPosition(pos);
        return entity;
    }
}
