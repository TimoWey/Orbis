package dev.orbis.engine.c2me;

import dev.orbis.engine.Tori;
import dev.orbis.engine.Torus;
import dev.orbis.engine.mixin.ChunkMapAccessor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ChunkMap;
import org.jspecify.annotations.Nullable;

/**
 * C2ME tags each chunk lock with the id of its world's scheduling manager and nothing else; this maps the id back
 * to the world, so a lock on a chunk past the seam can be taken on the chunk it is.
 */
public final class C2meWorlds {
    private static final Map<Integer, ChunkMap> WORLDS = new ConcurrentHashMap<>();

    public static void register(int schedulingId, ChunkMap map) { WORLDS.put(schedulingId, map); }

    public static @Nullable Torus torus(int schedulingId) {
        ChunkMap map = WORLDS.get(schedulingId);
        return map == null ? null : Tori.of(((ChunkMapAccessor) map).orbis$level());
    }

    private C2meWorlds() {}
}
