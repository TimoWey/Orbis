package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusHolder;
import java.util.function.Consumer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two views on a torus are compared in the frame of the new one, so walking over the seam only sends the
 * chunks that came into view instead of dropping and resending everything.
 */
@Mixin(ChunkTrackingView.class)
public interface ChunkTrackingViewMixin {
    @Inject(method = "difference", at = @At("HEAD"), cancellable = true)
    private static void orbis$torusDifference(ChunkTrackingView from, ChunkTrackingView to, Consumer<ChunkPos> onEnter,
                                              Consumer<ChunkPos> onLeave, CallbackInfo callback) {
        if (!(from instanceof ChunkTrackingView.Positioned last) || !(to instanceof ChunkTrackingView.Positioned next)) return;
        Torus torus = ((TorusHolder) (Object) next).orbis$torus();
        if (torus == null) return;
        callback.cancel();
        if (last.equals(next)) return;
        var lastCentre = new ChunkPos(torus.nearestChunk(last.center().x(), next.center().x()),
                torus.nearestChunk(last.center().z(), next.center().z()));
        ChunkTrackingView.difference(new ChunkTrackingView.Positioned(lastCentre, last.viewDistance()),
                new ChunkTrackingView.Positioned(next.center(), next.viewDistance()),
                pos -> onEnter.accept(Fold.chunk(torus, pos)), pos -> onLeave.accept(Fold.chunk(torus, pos)));
    }
}
