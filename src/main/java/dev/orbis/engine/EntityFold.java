package dev.orbis.engine;

import dev.orbis.engine.mixin.MoveControlAccess;
import dev.orbis.engine.mixin.PathNavigationAccess;
import dev.orbis.engine.net.ClientFrame;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Moves an entity that has stepped past the seam back into the world by whole widths, together with everything
 * that remembers where it was: its previous positions, a mob's path and move target, and its passengers. A player
 * riding along keeps walking in their own frame, so their lap takes the shift.
 */
public final class EntityFold {
    private EntityFold() {}

    public static void fold(@Nullable Torus torus, Entity entity) {
        if (torus == null) return;
        double x = entity.getX(), z = entity.getZ();
        int dx = (int) Math.round(torus.fold(x) - x), dz = (int) Math.round(torus.fold(z) - z);
        if (dx != 0 || dz != 0) shift(entity, dx, dz);
    }

    private static void shift(Entity entity, int dx, int dz) {
        if (entity instanceof BlockAttachedEntity attached) {
            attached.setPos(Vec3.atCenterOf(attached.getPos().offset(dx, 0, dz)));
        } else {
            entity.setPos(entity.getX() + dx, entity.getY(), entity.getZ() + dz);
        }
        entity.xo += dx;
        entity.zo += dz;
        entity.xOld += dx;
        entity.zOld += dz;
        if (entity instanceof Mob mob) shiftTargets(mob, dx, dz);
        if (entity instanceof ServerPlayer player) {
            if (player.connection instanceof ClientFrame.Holder holder) holder.orbis$frame().playerShifted(player, dx, dz);
            ((ServerLevel) player.level()).getChunkSource().move(player);
        }
        for (Entity passenger : entity.getPassengers()) shift(passenger, dx, dz);
    }

    /** A mob keeps following its path: the path, its target and the spot the mob was stepping to move along. */
    private static void shiftTargets(Mob mob, int dx, int dz) {
        var move = (MoveControlAccess) mob.getMoveControl();
        move.orbis$setWantedX(move.orbis$wantedX() + dx);
        move.orbis$setWantedZ(move.orbis$wantedZ() + dz);
        var navigation = mob.getNavigation();
        var access = (PathNavigationAccess) navigation;
        BlockPos target = access.orbis$targetPos();
        if (target != null) access.orbis$setTargetPos(target.offset(dx, 0, dz));
        Path path = navigation.getPath();
        if (path == null || path.isDone()) return;
        List<Node> nodes = new ArrayList<>(path.getNodeCount());
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            nodes.add(node.cloneAndMove(node.x + dx, node.y, node.z + dz));
        }
        Path moved = new Path(nodes, path.getTarget().offset(dx, 0, dz), path.canReach());
        moved.setNextNodeIndex(path.getNextNodeIndex());
        navigation.moveTo(moved, access.orbis$speedModifier());
    }
}
