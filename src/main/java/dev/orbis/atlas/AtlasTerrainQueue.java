package dev.orbis.atlas;

import java.util.*;
import net.minecraft.world.level.ChunkPos;

/** Owner-thread queue: capture nearby surfaces independently of biome/structure inspection. */
final class AtlasTerrainQueue {
    private final Set<Long> present = new HashSet<>();
    private int x, z;
    private final PriorityQueue<Long> queue = new PriorityQueue<>(Comparator
            .<Long>comparingLong(key -> distance(key, x, z)).thenComparingLong(Long::longValue));
    static long distance(long key, int x, int z) {
        long dx = (long) ChunkPos.getX(key) - x, dz = (long) ChunkPos.getZ(key) - z;
        return dx * dx + dz * dz;
    }
    void focus(int x, int z) {
        if (this.x == x && this.z == z) return;
        this.x = x; this.z = z;
        queue.clear(); queue.addAll(present);
    }
    void add(long key) {
        if (!present.add(key)) return;
        queue.add(key);
        // Lazy removal leaves stale entries behind; rebuild before they outnumber the live ones.
        if (queue.size() > 2 * present.size() + 64) { queue.clear(); queue.addAll(present); }
    }
    void addAll(Collection<Long> keys) { keys.forEach(this::add); }
    boolean contains(long key) { return present.contains(key); }
    boolean isEmpty() { return present.isEmpty(); }
    /** O(1): the queue entry stays behind and poll() skips it, avoiding a linear PriorityQueue.remove. */
    void remove(long key) { present.remove(key); }
    Long poll() {
        Long key;
        // A removed and re-added key may be queued twice; the first copy consumes its presence.
        while ((key = queue.poll()) != null && !present.remove(key)) { }
        return key;
    }
    int queued() { return queue.size(); }
}
