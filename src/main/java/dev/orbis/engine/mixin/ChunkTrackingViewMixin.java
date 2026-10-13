package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusHolder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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
        var entered = new LongOpenHashSet();
        ChunkTrackingView.difference(new ChunkTrackingView.Positioned(lastCentre, last.viewDistance()),
                new ChunkTrackingView.Positioned(next.center(), next.viewDistance()),
                pos -> {
                    ChunkPos folded = Fold.chunk(torus, pos);
                    if (entered.add(folded.pack())) onEnter.accept(folded);
                }, pos -> onLeave.accept(Fold.chunk(torus, pos)));
        // A view wider than the world keeps every chunk in sight, but the image nearest to the player still moves
        // as the player does. Those chunks are sent again, at the new image.
        if (2 * next.viewDistance() + 3 <= torus.widthChunks()) return;
        next.forEach(folded -> {
            boolean moved = torus.nearestChunk(folded.x(), lastCentre.x()) != torus.nearestChunk(folded.x(), next.center().x())
                    || torus.nearestChunk(folded.z(), lastCentre.z()) != torus.nearestChunk(folded.z(), next.center().z());
            if (moved && last.contains(folded.x(), folded.z()) && entered.add(folded.pack())) onEnter.accept(folded);
        });
    }
}
