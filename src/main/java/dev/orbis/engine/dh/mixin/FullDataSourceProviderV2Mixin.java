package dev.orbis.engine.dh.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.file.fullDatafile.V2.FullDataSourceProviderV2;
import dev.orbis.engine.Torus;
import dev.orbis.engine.dh.DhTorus;
import org.spongepowered.asm.mixin.Mixin;

/** Sections outside the world are read from their images inside it. */
@Mixin(value = FullDataSourceProviderV2.class, remap = false)
public abstract class FullDataSourceProviderV2Mixin {
    @WrapMethod(method = "get(J)Lcom/seibel/distanthorizons/core/dataObjects/fullData/sources/FullDataSourceV2;")
    private FullDataSourceV2 orbis$readHome(long pos, Operation<FullDataSourceV2> original) {
        Torus torus = DhTorus.of(((FullDataSourceProviderV2Accessor) this).orbis$levelWrapper());
        if (torus == null) return original.call(pos);
        return DhTorus.read(torus, pos, at -> original.call(at));
    }

    @WrapMethod(method = "getAdjForDirection")
    private FullDataSourceV2 orbis$readHomeEdge(long pos, EDhDirection direction, Operation<FullDataSourceV2> original) {
        Torus torus = DhTorus.of(((FullDataSourceProviderV2Accessor) this).orbis$levelWrapper());
        if (torus == null) return original.call(pos, direction);
        long home = DhTorus.home(torus, pos);
        FullDataSourceV2 data = original.call(home, direction);
        if (data != null && home != pos) DhTorus.move(data, pos);
        return data;
    }
}
