package dev.orbis.gametest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps closing a test world from locking up the client game test harness.
 *
 * <p>The harness runs the client's disconnect after both threads leave a lockstep phase, and
 * {@code halt} then waits for the server to run a task. The server only runs it if the task
 * arrives before it reaches the next phase. A slow disconnect, such as Distant Horizons closing
 * its databases, lets the server get there first, and each thread then waits on the other.
 * Handing the task over without waiting keeps the order; the server runs it on its next tick.
 */
@Mixin(IntegratedServer.class)
abstract class IntegratedServerMixin {
    @WrapOperation(method = "halt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/server/IntegratedServer;executeBlocking(Ljava/lang/Runnable;)V"))
    private void orbisGametest$handOver(IntegratedServer server, Runnable task, Operation<Void> original) {
        if (server.isSameThread()) original.call(server, task);
        else server.execute(task);
    }
}
