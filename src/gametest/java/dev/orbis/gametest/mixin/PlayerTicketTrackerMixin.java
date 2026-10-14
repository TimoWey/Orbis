package dev.orbis.gametest.mixin;

import dev.orbis.gametest.SeamTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records when a chunk near the seam comes into or leaves a player's view, for a failure message. */
@Mixin(targets = "net.minecraft.server.level.DistanceManager$PlayerTicketTracker")
abstract class PlayerTicketTrackerMixin {
    @Inject(method = "onLevelChange(JIZZ)V", at = @At("HEAD"))
    private void orbisGametest$trace(long key, int level, boolean saw, boolean sees, CallbackInfo callback) {
        if (saw != sees) SeamTrace.record(key, (sees ? "sees" : "no longer sees") + " at level " + level);
    }
}
