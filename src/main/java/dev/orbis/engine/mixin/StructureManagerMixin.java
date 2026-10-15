package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Fold;
import dev.orbis.engine.StructureFrames;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * During generation, every structure start is handed out in the frame of the chunk being generated, so pieces
 * across the seam are placed and the terrain adapts to them. At runtime, a position is inside a structure when
 * its image nearest to the structure is.
 */
@Mixin(StructureManager.class)
public abstract class StructureManagerMixin {
    @Shadow @Final private LevelAccessor level;

    @Unique
    private @Nullable Torus orbis$torus() {
        return level instanceof ServerLevelAccessor server ? Tori.of(server.getLevel()) : null;
    }

    @ModifyVariable(method = "fillStartsForStructure", at = @At("HEAD"), argsOnly = true)
    private Consumer<StructureStart> orbis$inGenerationFrame(Consumer<StructureStart> consumer) {
        Torus torus = orbis$torus();
        if (torus == null || !(level instanceof WorldGenRegion region)) return consumer;
        return start -> consumer.accept(StructureFrames.toward(torus, region.getLevel(), start, region.getCenter()));
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;isInside(Lnet/minecraft/core/Vec3i;)Z"))
    private boolean orbis$insideNearest(BoundingBox box, Vec3i pos, Operation<Boolean> original) {
        Torus torus = orbis$torus();
        if (torus == null) return original.call(box, pos);
        BlockPos centre = box.getCenter();
        return original.call(box, Fold.nearest(torus, new BlockPos(pos.getX(), pos.getY(), pos.getZ()), centre.getX() + 0.5, centre.getZ() + 0.5));
    }
}
