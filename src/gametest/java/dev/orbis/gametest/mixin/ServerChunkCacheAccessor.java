package dev.orbis.gametest.mixin;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The tickets on a chunk, for a test's failure message. */
@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccessor {
    @Accessor("ticketStorage") TicketStorage orbisGametest$tickets();
}
