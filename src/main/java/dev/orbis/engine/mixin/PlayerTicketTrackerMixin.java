package dev.orbis.engine.mixin;

import dev.orbis.engine.TorusHolder;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A chunk keeps its player loading ticket for as long as a player sees it. The game decides whether a chunk had a
 * ticket from the level its ticket queue last saw, which a worker thread updates later. A player who glides a few
 * chunks away and is put back before that update lands loses the ticket and never gets it again: the chunk next to
 * them stays loaded, but its mobs never move. Here the decision follows the tickets actually handed out.
 */
@Mixin(targets = "net.minecraft.server.level.DistanceManager$PlayerTicketTracker")
public abstract class PlayerTicketTrackerMixin {
    @Shadow private int viewDistance;
    @Shadow @Final private LongSet toUpdate;

    @Shadow protected abstract void onLevelChange(long key, int level, boolean saw, boolean sees);

    @Unique private final LongSet orbis$seen = new LongOpenHashSet();
    @Unique private final Long2IntMap orbis$levels = new Long2IntOpenHashMap();

    @Unique
    private boolean orbis$onTorus() {
        return (Object) this instanceof TorusHolder holder && holder.orbis$torus() != null;
    }

    @Inject(method = "onLevelChange(JII)V", at = @At("HEAD"))
    private void orbis$noteLevel(long node, int oldLevel, int level, CallbackInfo callback) {
        // Only tori settle tickets below; elsewhere (the End, normal worlds) nothing would ever remove the entry.
        if (orbis$onTorus()) orbis$levels.put(node, level);
    }

    @ModifyVariable(method = "onLevelChange(JIZZ)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean orbis$sawTicket(boolean saw, long key, int level, boolean sawArgument, boolean sees) {
        return orbis$onTorus() ? orbis$seen.contains(key) : saw;
    }

    @Inject(method = "onLevelChange(JIZZ)V", at = @At("RETURN"))
    private void orbis$noteTicket(long key, int level, boolean saw, boolean sees, CallbackInfo callback) {
        if (!orbis$onTorus()) return;
        if (sees) orbis$seen.add(key);
        else orbis$seen.remove(key);
    }

    /** A chunk whose level came back to what the queue last saw is skipped by the game; it is settled here. */
    @Inject(method = "runAllUpdates", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongSet;clear()V"))
    private void orbis$settle(CallbackInfo callback) {
        if (!orbis$onTorus()) return;
        for (LongIterator it = toUpdate.iterator(); it.hasNext(); ) {
            long node = it.nextLong();
            int level = orbis$levels.getOrDefault(node, Integer.MAX_VALUE);
            boolean sees = level <= viewDistance;
            if (orbis$seen.contains(node) != sees) onLevelChange(node, level, !sees, sees);
            if (level > viewDistance + 2) orbis$levels.remove(node);
        }
    }
}
