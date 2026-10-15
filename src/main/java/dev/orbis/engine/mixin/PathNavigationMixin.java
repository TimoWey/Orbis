package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A path leads to the image of each target nearest to the mob. Blocks are read through the folding chunk lookup, so
 * the search itself may run past the seam.
 */
@Mixin(PathNavigation.class)
public abstract class PathNavigationMixin {
    @Shadow @Final protected Mob mob;

    @ModifyVariable(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), argsOnly = true)
    private Set<BlockPos> orbis$nearestTargets(Set<BlockPos> targets) {
        Torus torus = Tori.of(mob.level());
        if (torus == null) return targets;
        Set<BlockPos> nearest = new HashSet<>(targets.size());
        for (BlockPos target : targets) nearest.add(Fold.nearest(torus, target, mob.getX(), mob.getZ()));
        return nearest;
    }
}
