package dev.orbis.engine.dh.mixin;

import com.seibel.distanthorizons.core.file.fullDatafile.V2.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = FullDataSourceProviderV2.class, remap = false)
public interface FullDataSourceProviderV2Accessor {
    @Accessor("levelWrapper") ILevelWrapper orbis$levelWrapper();
}
