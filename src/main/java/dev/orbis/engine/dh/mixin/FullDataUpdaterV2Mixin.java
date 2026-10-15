package dev.orbis.engine.dh.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.file.fullDatafile.V2.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.file.fullDatafile.V2.FullDataUpdaterV2;
import dev.orbis.engine.Torus;
import dev.orbis.engine.dh.DhTorus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Terrain for a section outside the world (chunks the client holds past the seam, or anything DH built there) is
 * stored at its image inside the world, so there is one copy, and every image shows the same changes.
 */
@Mixin(value = FullDataUpdaterV2.class, remap = false)
public abstract class FullDataUpdaterV2Mixin {
    @Shadow @Final private FullDataSourceProviderV2 provider;

    @WrapMethod(method = "updateDataSource")
    private void orbis$writeHome(FullDataSourceV2 input, Operation<Void> original) {
        Torus torus = DhTorus.of(((FullDataSourceProviderV2Accessor) provider).orbis$levelWrapper());
        long pos = input.getPos(), home = torus == null ? pos : DhTorus.home(torus, pos);
        if (home == pos) { original.call(input); return; }
        DhTorus.move(input, home);
        try { original.call(input); }
        finally { DhTorus.move(input, pos); }
    }
}
