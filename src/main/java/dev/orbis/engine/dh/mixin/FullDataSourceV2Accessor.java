package dev.orbis.engine.dh.mixin;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A section read from its image inside the world is handed out at the position it was asked for. */
@Mixin(value = FullDataSourceV2.class, remap = false)
public interface FullDataSourceV2Accessor {
    @Mutable @Accessor("pos") void orbis$setPos(long pos);
}
