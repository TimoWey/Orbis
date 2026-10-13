package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusHolder;
import net.minecraft.core.BlockPos;
import java.util.function.Consumer;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.Continuation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
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

    /** Entities are stored folded, so a box reaching past the seam is searched as its parts on the torus. */
    @WrapOperation(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/LevelEntityGetter;get(Lnet/minecraft/world/phys/AABB;Ljava/util/function/Consumer;)V"))
    private void orbis$entitiesInPieces(LevelEntityGetter<Entity> entities, AABB box, Consumer<Entity> output,
                                        Operation<Void> original) {
        Fold.pieces(orbis$torus, box, piece -> original.call(entities, piece, output));
    }

    @WrapOperation(method = {
            "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V",
            "hasEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Z"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/LevelEntityGetter;get(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void orbis$typedEntitiesInPieces(LevelEntityGetter<Entity> entities, EntityTypeTest type, AABB box,
                                             AbortableIterationConsumer output, Operation<Void> original) {
        boolean[] aborted = {false};
        AbortableIterationConsumer tracked = entity -> {
            Continuation next = output.accept(entity);
            if (next.shouldAbort()) aborted[0] = true;
            return next;
        };
        Fold.pieces(orbis$torus, box, piece -> {
            if (!aborted[0]) original.call(entities, type, piece, tracked);
        });
    }

    @Override public @Nullable Torus orbis$torus() { return orbis$torus; }

    @Override public void orbis$setTorus(@Nullable Torus torus) { orbis$torus = torus; }
}
