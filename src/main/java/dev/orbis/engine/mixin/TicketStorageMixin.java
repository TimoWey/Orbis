package dev.orbis.engine.mixin;

import dev.orbis.engine.Fold;
import dev.orbis.engine.Torus;
import dev.orbis.engine.TorusHolder;
import net.minecraft.world.level.TicketStorage;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every ticket is stored at its folded chunk, whichever path added it, so adding and removing always match. */
@Mixin(TicketStorage.class)
public abstract class TicketStorageMixin implements TorusHolder {
    @Unique private @Nullable Torus orbis$torus;

    @ModifyVariable(method = {"addTicket(JLnet/minecraft/server/level/Ticket;)Z", "removeTicket(JLnet/minecraft/server/level/Ticket;)Z"},
            at = @At("HEAD"), argsOnly = true)
    private long orbis$foldTicket(long chunk) { return Fold.chunk(orbis$torus, chunk); }

    @Override public @Nullable Torus orbis$torus() { return orbis$torus; }

    @Override public void orbis$setTorus(@Nullable Torus torus) { orbis$torus = torus; }
}
