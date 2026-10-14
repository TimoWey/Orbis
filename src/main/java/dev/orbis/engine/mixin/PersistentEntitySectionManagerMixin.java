package dev.orbis.engine.mixin;

import dev.orbis.engine.EntityFold;
import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An entity that joins past the seam (spawned, placed or loaded there) joins at its folded position. */
@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin<T extends EntityAccess> implements TorusHolder {
    @Shadow @Final private EntitySectionStorage<T> sectionStorage;
    @Unique private @Nullable Torus orbis$torus;

    @Inject(method = "addEntity(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z", at = @At("HEAD"))
    private void orbis$foldJoining(T entity, boolean loaded, CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof Entity joining && !(joining instanceof ServerPlayer)) EntityFold.fold(orbis$torus, joining);
    }

    @Override public @Nullable Torus orbis$torus() { return orbis$torus; }

    @Override public void orbis$setTorus(@Nullable Torus torus) {
        orbis$torus = torus;
        ((TorusHolder) sectionStorage).orbis$setTorus(torus);
    }
}
