package dev.orbis.atlas;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import net.minecraft.world.level.ChunkPos;

/** Owner-thread capture, shared by received multiplayer chunks and local server terrain. */
final class AtlasTerrainCollector {
    static final int PER_TICK = 32, HISTORY_LIMIT = 8192;
    static final long BUDGET_NANOS = 2_000_000;
    final Set<Long> loaded = new HashSet<>();
    private final AtlasTerrainQueue pending = new AtlasTerrainQueue();
    private final LinkedHashMap<Long, AtlasSnapshot.Cell> history = new LinkedHashMap<>();
    private final LongSupplier clock;
    private List<AtlasSnapshot.Cell> cached = List.of();
    private long revision, cachedRevision = -1;
    private int ticks;
    AtlasTerrainCollector() { this(System::nanoTime); }
    AtlasTerrainCollector(LongSupplier clock) { this.clock = clock; }
    void load(long key) { loaded.add(key); pending.add(key); }
    void unload(long key, Function<Long, AtlasSnapshot.Cell> sample, Consumer<AtlasSnapshot.Cell> captured) {
        if (pending.contains(key)) {
            var tile = sample.apply(key);
            if (tile != null) { accept(tile); captured.accept(tile); }
        }
        loaded.remove(key); pending.remove(key);
    }
    int tick(int x, int z, Function<Long, AtlasSnapshot.Cell> sample, Consumer<AtlasSnapshot.Cell> captured) {
        if (++ticks % 200 == 0) pending.addAll(loaded);
        pending.focus(x, z);
        long deadline = clock.getAsLong() + BUDGET_NANOS;
        int count = 0;
        while (count < PER_TICK && clock.getAsLong() < deadline) {
            Long key = pending.poll();
            if (key == null) break;
            var tile = sample.apply(key);
            if (tile != null) { accept(tile); captured.accept(tile); }
            count++;
        }
        return count;
    }
    void accept(AtlasSnapshot.Cell tile) {
        long key = ChunkPos.pack(tile.x(), tile.z());
        var previous = history.get(key);
        if (previous != null && previous.sameTerrain(tile)) return;
        history.remove(key); history.put(key, tile); revision++;
        while (history.size() > HISTORY_LIMIT) history.remove(history.keySet().iterator().next());
    }
    List<AtlasSnapshot.Cell> cells() {
        if (cachedRevision != revision) { cached = List.copyOf(history.values()); cachedRevision = revision; }
        return cached;
    }
    long revision() { return revision; }
}
