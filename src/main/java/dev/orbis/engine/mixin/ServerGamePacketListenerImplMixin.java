package dev.orbis.engine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orbis.engine.Fold;
import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import dev.orbis.engine.net.ClientFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Positions from the client arrive in its continuous frame. The server reads each one as the image nearest to the
 * player, which is the same block or spot whichever lap the client is on; this is idempotent, so handlers that are
 * re-queued onto the server thread can apply it twice. After a move the player is folded back into the world.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin implements ClientFrame.Holder {
    private static final String MOVE = "handlePlayerPositionChange(DDDFFZZ)V";
    private static final String CLAMP = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;clampHorizontal(D)D";

    @Shadow public ServerPlayer player;
    @Shadow private double firstGoodX;
    @Shadow private double firstGoodZ;
    @Shadow private double lastGoodX;
    @Shadow private double lastGoodZ;

    @Unique private final ClientFrame orbis$frame = new ClientFrame();

    @Override public ClientFrame orbis$frame() { return orbis$frame; }

    @WrapOperation(method = MOVE, at = @At(value = "INVOKE", target = CLAMP, ordinal = 0))
    private double orbis$moveX(double x, Operation<Double> original) {
        double clamped = original.call(x);
        Torus torus = Tori.of(player.level());
        return torus == null ? clamped : torus.nearest(clamped, player.getX());
    }

    @WrapOperation(method = MOVE, at = @At(value = "INVOKE", target = CLAMP, ordinal = 1))
    private double orbis$moveZ(double z, Operation<Double> original) {
        double clamped = original.call(z);
        Torus torus = Tori.of(player.level());
        return torus == null ? clamped : torus.nearest(clamped, player.getZ());
    }

    /** A vehicle the player steers arrives in the client's frame too; it is read near the vehicle. */
    @WrapOperation(method = "handleMoveVehicle", at = @At(value = "INVOKE", target = CLAMP, ordinal = 0))
    private double orbis$vehicleX(double x, Operation<Double> original) {
        double clamped = original.call(x);
        Torus torus = Tori.of(player.level());
        return torus == null ? clamped : torus.nearest(clamped, player.getRootVehicle().getX());
    }

    @WrapOperation(method = "handleMoveVehicle", at = @At(value = "INVOKE", target = CLAMP, ordinal = 1))
    private double orbis$vehicleZ(double z, Operation<Double> original) {
        double clamped = original.call(z);
        Torus torus = Tori.of(player.level());
        return torus == null ? clamped : torus.nearest(clamped, player.getRootVehicle().getZ());
    }

    /** Crossing the seam: the server moves the player by one width, the client keeps walking. */
    @Inject(method = MOVE, at = @At("RETURN"))
    private void orbis$foldPlayer(CallbackInfo callback) {
        Torus torus = Tori.of(player.level());
        if (torus == null || player.isPassenger()) return;
        double x = player.getX(), z = player.getZ();
        int dx = (int) Math.round(torus.fold(x) - x), dz = (int) Math.round(torus.fold(z) - z);
        if (dx == 0 && dz == 0) return;
        player.setPos(x + dx, player.getY(), z + dz);
        player.xo += dx;
        player.zo += dz;
        player.xOld += dx;
        player.zOld += dz;
        firstGoodX += dx;
        firstGoodZ += dz;
        lastGoodX += dx;
        lastGoodZ += dz;
        orbis$frame.playerShifted(player, dx, dz);
    }

    @ModifyVariable(method = "handleUseItemOn", at = @At("HEAD"), argsOnly = true)
    private ServerboundUseItemOnPacket orbis$nearestHit(ServerboundUseItemOnPacket packet) {
        Torus torus = Tori.of(player.level());
        if (torus == null) return packet;
        BlockHitResult hit = packet.hitResult();
        BlockPos pos = hit.getBlockPos(), near = Fold.nearest(torus, pos, player.getX(), player.getZ());
        if (near.equals(pos)) return packet;
        var moved = new BlockHitResult(hit.getLocation().add(near.getX() - pos.getX(), 0, near.getZ() - pos.getZ()),
                hit.getDirection(), near, hit.isInside());
        return new ServerboundUseItemOnPacket(packet.hand(), moved, packet.sequence());
    }

    @ModifyVariable(method = "handlePlayerAction", at = @At("HEAD"), argsOnly = true)
    private ServerboundPlayerActionPacket orbis$nearestAction(ServerboundPlayerActionPacket packet) {
        Torus torus = Tori.of(player.level());
        if (torus == null) return packet;
        BlockPos pos = packet.getPos(), near = Fold.nearest(torus, pos, player.getX(), player.getZ());
        return near.equals(pos) ? packet
                : new ServerboundPlayerActionPacket(packet.getAction(), near, packet.getDirection(), packet.getSequence());
    }
}
