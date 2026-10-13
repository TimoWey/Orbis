package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusHolder;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Entities are filed in the section at their folded position, also while one is briefly past the seam. */
@Mixin(EntitySectionStorage.class)
public abstract class EntitySectionStorageMixin implements TorusHolder {
    @Unique private @Nullable Torus orbis$torus;

    @ModifyVariable(method = {"getOrCreateSection", "getSection", "remove"}, at = @At("HEAD"), argsOnly = true)
    private long orbis$foldSection(long section) { return Fold.section(orbis$torus, section); }

    @Override public @Nullable Torus orbis$torus() { return orbis$torus; }

    @Override public void orbis$setTorus(@Nullable Torus torus) { orbis$torus = torus; }
}
