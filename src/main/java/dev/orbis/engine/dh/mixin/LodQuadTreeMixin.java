package dev.orbis.engine.dh.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seibel.distanthorizons.core.file.fullDatafile.V2.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.generation.tasks.DataSourceRetrievalResult;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.QuadTree.LodQuadTree;
import dev.orbis.engine.Torus;
import dev.orbis.engine.dh.DhTorus;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The tree of far terrain around the player stays in the client's frame. It asks for generation at the images inside
 * the world, and rebuilds every image in view of a section that changed.
 */
@Mixin(value = LodQuadTree.class, remap = false)
public abstract class LodQuadTreeMixin {
    @Shadow @Final private FullDataSourceProviderV2 fullDataSourceProvider;

    private Torus orbis$torus() {
        return DhTorus.of(((FullDataSourceProviderV2Accessor) fullDataSourceProvider).orbis$levelWrapper());
    }

    @WrapOperation(method = "tryQueuePosForRetrieval", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/file/fullDatafile/V2/FullDataSourceProviderV2;getPositionsToRetrieve(JB)Lit/unimi/dsi/fastutil/longs/LongArrayList;"))
    private LongArrayList orbis$missingAtHome(FullDataSourceProviderV2 provider, long pos, byte detail, Operation<LongArrayList> original) {
        Torus torus = orbis$torus();
        if (torus == null) return original.call(provider, pos, detail);
        return DhTorus.missing(torus, pos, detail, (at, level) -> original.call(provider, at, level));
    }

    @WrapOperation(method = "startQueuedRetrievalTasks", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/file/fullDatafile/V2/FullDataSourceProviderV2;queuePositionForRetrieval(Ljava/lang/Long;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<DataSourceRetrievalResult> orbis$generateAtHome(FullDataSourceProviderV2 provider, Long pos,
                                                                              Operation<CompletableFuture<DataSourceRetrievalResult>> original) {
        Torus torus = orbis$torus();
        return original.call(provider, torus == null ? pos : (Long) DhTorus.home(torus, pos));
    }

    @WrapMethod(method = "queuePosToReload")
    private void orbis$reloadImages(long pos, Operation<Void> original) {
        original.call(pos);
        Torus torus = orbis$torus();
        if (torus == null) return;
        var tree = (LodQuadTree) (Object) this;
        int span = torus.widthBlocks(), radius = tree.diameterInBlocks() / 2 + DhSectionPos.getBlockWidth(pos);
        var center = tree.getCenterBlockPos();
        LongOpenHashSet coarse = new LongOpenHashSet();
        int x = DhSectionPos.getMinCornerBlockX(pos), z = DhSectionPos.getMinCornerBlockZ(pos);
        boolean images = DhTorus.translatable(torus, pos);
        int fromX = images ? Math.floorDiv(center.x - radius - x, span) : 0, toX = images ? Math.floorDiv(center.x + radius - x, span) + 1 : 0;
        int fromZ = images ? Math.floorDiv(center.z - radius - z, span) : 0, toZ = images ? Math.floorDiv(center.z + radius - z, span) + 1 : 0;
        for (int kx = fromX; kx <= toX; kx++) for (int kz = fromZ; kz <= toZ; kz++) {
            long image = kx == 0 && kz == 0 ? pos : DhTorus.translate(pos, kx * span, kz * span);
            if (image != pos && tree.isSectionPosInBounds(image)) original.call(image);
            // Sections wider than the world hold several images at once; each must be rebuilt.
            for (long parent = image; DhSectionPos.getDetailLevel(parent) < 12; ) {
                parent = DhSectionPos.getParentPos(parent);
                if (!DhTorus.translatable(torus, parent) && coarse.add(parent) && tree.isSectionPosInBounds(parent)) original.call(parent);
            }
        }
    }
}
