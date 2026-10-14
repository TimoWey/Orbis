package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusHolder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A player who has just stepped past the seam is counted before the server folds them. Their chunk is counted at
 * its folded position: a source at a position no neighbour points back to would keep its surroundings loaded
 * forever, and saving the world would wait for them to unload.
 */
@Mixin(DistanceManager.class)
public abstract class DistanceManagerMixin implements TorusHolder {
    @Unique private @Nullable Torus orbis$torus;

    @ModifyVariable(method = {"addPlayer", "removePlayer"}, at = @At("HEAD"), argsOnly = true)
    private SectionPos orbis$foldPlayer(SectionPos pos) { return Fold.section(orbis$torus, pos); }

    @Override public @Nullable Torus orbis$torus() { return orbis$torus; }

    @Override public void orbis$setTorus(@Nullable Torus torus) { orbis$torus = torus; }
}
