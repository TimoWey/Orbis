package dev.orbis.gametest.mixin;

import dev.orbis.gametest.SeamTrace;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records player loading tickets added to and removed from chunks near the seam, for a failure message. */
@Mixin(TicketStorage.class)
abstract class TicketStorageMixin {
    @Inject(method = "addTicket(JLnet/minecraft/server/level/Ticket;)Z", at = @At("RETURN"))
    private void orbisGametest$traceAdd(long key, Ticket ticket, CallbackInfoReturnable<Boolean> callback) {
        if (ticket.getType() == TicketType.PLAYER_LOADING) SeamTrace.record(key, "ticket added " + callback.getReturnValue());
    }

    @Inject(method = "removeTicket(JLnet/minecraft/server/level/Ticket;)Z", at = @At("RETURN"))
    private void orbisGametest$traceRemove(long key, Ticket ticket, CallbackInfoReturnable<Boolean> callback) {
        if (ticket.getType() == TicketType.PLAYER_LOADING) SeamTrace.record(key, "ticket removed " + callback.getReturnValue());
    }
}
