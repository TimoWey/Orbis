package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** A section on the seam has its neighbours on the other side of the world. */
@Mixin(LayerLightSectionStorage.class)
public abstract class LayerLightSectionStorageMixin {
    @Shadow @Final protected LightChunkGetter chunkSource;

    @WrapOperation(method = "updateSectionStatus", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;offset(JIII)J"))
    private long orbis$foldNeighbour(long section, int dx, int dy, int dz, Operation<Long> original) {
        long neighbour = original.call(section, dx, dy, dz);
        return chunkSource.getLevel() instanceof Level level ? Fold.section(Tori.of(level), neighbour) : neighbour;
    }
}
