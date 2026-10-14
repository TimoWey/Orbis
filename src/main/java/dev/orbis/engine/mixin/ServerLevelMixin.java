package dev.orbis.engine.mixin;

import dev.orbis.engine.EntityFold;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import dev.orbis.engine.TorusHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Shadow @Final private LevelTicks<Block> blockTicks;
    @Shadow @Final private LevelTicks<Fluid> fluidTicks;
    @Shadow @Final private PersistentEntitySectionManager<Entity> entityManager;
    @Shadow @Final protected Raids raids;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void orbis$foldTicks(CallbackInfo callback) {
        var torus = Tori.of((ServerLevel) (Object) this);
        ((TorusHolder) blockTicks).orbis$setTorus(torus);
        ((TorusHolder) fluidTicks).orbis$setTorus(torus);
        ((TorusHolder) entityManager).orbis$setTorus(torus);
        ((TorusHolder) raids).orbis$setTorus(torus);
    }

    /** An entity that walked, flew or was pushed past the seam this tick is moved back into the world. */
    @Inject(method = "tickNonPassenger", at = @At("TAIL"))
    private void orbis$foldEntity(Entity entity, CallbackInfo callback) {
        if (!(entity instanceof Player) && !entity.isRemoved()) EntityFold.fold(Tori.of((ServerLevel) (Object) this), entity);
    }

    /**
     * Whether a chunk's entities are loaded is asked of the chunk, by any of its images: a player joining next to the
     * seam waits for the chunks around it, some of them past the seam, and would otherwise wait forever.
     */
    @ModifyVariable(method = "areEntitiesLoaded", at = @At("HEAD"), argsOnly = true)
    private long orbis$foldEntitiesLoaded(long chunk) { return Fold.chunk(Tori.of((ServerLevel) (Object) this), chunk); }

    @ModifyVariable(method = "areEntitiesActuallyLoadedAndTicking", at = @At("HEAD"), argsOnly = true)
    private ChunkPos orbis$foldEntitiesTicking(ChunkPos chunk) { return Fold.chunk(Tori.of((ServerLevel) (Object) this), chunk); }

    /** Block events are de-duplicated and replayed by position, so they name the folded block. */
    @ModifyVariable(method = "blockEvent", at = @At("HEAD"), argsOnly = true)
    private BlockPos orbis$foldBlockEvent(BlockPos pos) { return Fold.block(Tori.of((ServerLevel) (Object) this), pos); }
}
