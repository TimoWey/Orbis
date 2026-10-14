package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.behavior.AssignProfessionFromJobSite;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory;
import net.minecraft.world.entity.ai.behavior.StrollAroundPoi;
import net.minecraft.world.entity.ai.behavior.StrollToPoi;
import net.minecraft.world.entity.ai.behavior.StrollToPoiList;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Villager behaviours that remember a point of interest keep its folded position, and measure it from the mob the
 * short way: a bed or workstation just past the seam is near, not a world away, so it is neither forgotten as too far
 * nor walked to the long way round. These run as static lambdas, so the level comes from their arguments.
 */
@Mixin({AcquirePoi.class, AssignProfessionFromJobSite.class, SetWalkTargetFromBlockMemory.class, StrollAroundPoi.class, StrollToPoi.class,
        StrollToPoiList.class, ValidateNearbyPoi.class})
public abstract class PoiBehaviourMixin {
    /** The claimed point is where the path ended, an image that may lie past the seam; it is stored folded. */
    @ModifyExpressionValue(method = "*", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/pathfinder/Path;getTarget()Lnet/minecraft/core/BlockPos;"))
    private static BlockPos orbis$foldClaimed(BlockPos target, @Local(argsOnly = true) ServerLevel level) {
        return Fold.block(Tori.of(level), target);
    }

    @WrapOperation(method = "*", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"))
    private static boolean orbis$nearPoi(BlockPos pos, Position at, double distance, Operation<Boolean> original,
                                         @Local(argsOnly = true) ServerLevel level) {
        return original.call(Fold.nearest(Tori.of(level), pos, at.x(), at.z()), at, distance);
    }

    @WrapOperation(method = "*", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distManhattan(Lnet/minecraft/core/Vec3i;)I"))
    private static int orbis$manhattan(BlockPos pos, Vec3i other, Operation<Integer> original, @Local(argsOnly = true) ServerLevel level) {
        return original.call(Fold.nearest(Tori.of(level), pos, other.getX() + 0.5, other.getZ() + 0.5), other);
    }

    @WrapOperation(method = "*", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;atBottomCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 orbis$towardPoi(Vec3i pos, Operation<Vec3> original, @Local(argsOnly = true) ServerLevel level,
                                        @Local(argsOnly = true) net.minecraft.world.entity.npc.villager.Villager body) {
        Vec3 at = original.call(pos);
        return Fold.nearest(Tori.of(level), at, body.position());
    }
}
