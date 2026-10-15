package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusHolder;
import java.util.function.Consumer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A player's view on a torus covers the nearest image of every chunk and lists chunks by folded position. */
@Mixin(ChunkTrackingView.Positioned.class)
public abstract class ChunkTrackingViewPositionedMixin implements TorusHolder {
    @Shadow @Final private ChunkPos center;
    @Unique private @Nullable Torus orbis$torus;

    @ModifyVariable(method = "contains(IIZ)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int orbis$nearestX(int x) { return orbis$torus == null ? x : orbis$torus.nearestChunk(x, center.x()); }

    @ModifyVariable(method = "contains(IIZ)Z", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int orbis$nearestZ(int z) { return orbis$torus == null ? z : orbis$torus.nearestChunk(z, center.z()); }

    @ModifyVariable(method = "forEach", at = @At("HEAD"), argsOnly = true)
    private Consumer<ChunkPos> orbis$folded(Consumer<ChunkPos> consumer) {
        Torus torus = orbis$torus;
        return torus == null ? consumer : pos -> consumer.accept(Fold.chunk(torus, pos));
    }

    @Override public @Nullable Torus orbis$torus() { return orbis$torus; }

    @Override public void orbis$setTorus(@Nullable Torus torus) { orbis$torus = torus; }
}
