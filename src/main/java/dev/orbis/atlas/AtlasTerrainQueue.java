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
    void add(long key) { if (present.add(key)) queue.add(key); }
    void addAll(Collection<Long> keys) { keys.forEach(this::add); }
    boolean contains(long key) { return present.contains(key); }
    boolean isEmpty() { return present.isEmpty(); }
    void remove(long key) { if (present.remove(key)) queue.remove(key); }
    Long poll() {
        Long key = queue.poll();
        if (key != null) present.remove(key);
        return key;
    }
}
