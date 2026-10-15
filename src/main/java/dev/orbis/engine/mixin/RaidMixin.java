package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.stream.Stream;

/**
 * A raid near the seam: raiders past it count as close to the village, waves spawn at folded places, and a centre
 * that has to move looks for village sections past the seam too.
 */
@Mixin(Raid.class)
public abstract class RaidMixin {
    @WrapOperation(method = "updateRaiders", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"))
    private double orbis$raiderShortWay(BlockPos center, Vec3i raider, Operation<Double> original, @Local(argsOnly = true) ServerLevel level) {
        return original.call(Fold.nearest(Tori.of(level), center, raider.getX() + 0.5, raider.getZ() + 0.5), raider);
    }

    @ModifyVariable(method = "findRandomSpawnPos", at = @At("STORE"), name = "spawnX")
    private int orbis$foldSpawnX(int x, @Local(argsOnly = true) ServerLevel level) {
        Torus torus = Tori.of(level);
        return torus == null ? x : torus.fold(x);
    }

    @ModifyVariable(method = "findRandomSpawnPos", at = @At("STORE"), name = "spawnZ")
    private int orbis$foldSpawnZ(int z, @Local(argsOnly = true) ServerLevel level) {
        Torus torus = Tori.of(level);
        return torus == null ? z : torus.fold(z);
    }

    @WrapOperation(method = "moveRaidCenterToNearbyVillageSection", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;cube(Lnet/minecraft/core/SectionPos;I)Ljava/util/stream/Stream;"))
    private Stream<SectionPos> orbis$foldSections(SectionPos center, int radius, Operation<Stream<SectionPos>> original,
                                                  @Local(argsOnly = true) ServerLevel level) {
        Torus torus = Tori.of(level);
        return original.call(center, radius).map(section -> Fold.section(torus, section));
    }
}
