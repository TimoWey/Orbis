package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A chunk keys its block entities by world position. A position from another image of the chunk (which only
 * occurs on a torus) is moved into this chunk, so lookups and saving see one block entity per block.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @ModifyVariable(method = {"setBlockState",
            "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            "getBlockEntityNbtForSaving", "removeBlockEntity"}, at = @At("HEAD"), argsOnly = true)
    private BlockPos orbis$inChunk(BlockPos pos) { return Fold.inChunk(pos, ((ChunkAccess) (Object) this).getPos()); }

    @ModifyVariable(method = {"setBlockEntity", "addAndRegisterBlockEntity"}, at = @At("HEAD"), argsOnly = true)
    private BlockEntity orbis$entityInChunk(BlockEntity entity) {
        BlockPos pos = Fold.inChunk(entity.getBlockPos(), ((ChunkAccess) (Object) this).getPos());
        if (pos != entity.getBlockPos()) ((BlockEntityAccessor) entity).orbis$setWorldPosition(pos);
        return entity;
    }
}
