package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.raid.Raids;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A level's raids are found the short way: a player past the seam is still in the raid of the village behind it, and
 * a raid's centre is the middle of its village's homes as they lie around the player, folded into the world.
 */
@Mixin(Raids.class)
public abstract class RaidsMixin implements TorusHolder {
    @Unique private @Nullable Torus orbis$torus;

    @WrapOperation(method = "getNearbyRaid", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"))
    private double orbis$shortWay(BlockPos center, net.minecraft.core.Vec3i pos, Operation<Double> original) {
        return original.call(Fold.nearest(orbis$torus, center, pos.getX() + 0.5, pos.getZ() + 0.5), pos);
    }

    @WrapOperation(method = "createOrExtendRaid", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/village/poi/PoiRecord;getPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos orbis$homeNearPlayer(PoiRecord record, Operation<BlockPos> original, @Local(argsOnly = true) BlockPos raidPosition) {
        return Fold.nearest(orbis$torus, original.call(record), raidPosition.getX() + 0.5, raidPosition.getZ() + 0.5);
    }

    @WrapOperation(method = "createOrExtendRaid", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;containing(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos orbis$foldCenter(Position pos, Operation<BlockPos> original) { return Fold.block(orbis$torus, original.call(pos)); }

    @Override public @Nullable Torus orbis$torus() { return orbis$torus; }

    @Override public void orbis$setTorus(@Nullable Torus torus) { orbis$torus = torus; }
}
