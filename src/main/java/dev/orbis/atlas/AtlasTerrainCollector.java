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
    private final AtlasTerrainQueue refresh = new AtlasTerrainQueue();
    private final LinkedHashMap<Long, AtlasSnapshot.Cell> history = new LinkedHashMap<>();
    private final LongSupplier clock;
    private List<AtlasSnapshot.Cell> cached = List.of();
    private Set<Long> cachedLoaded = Set.of();
    private long loadedRevision, cachedLoadedRevision = -1;
    private long revision, cachedRevision = -1;
    private int ticks, sinceRefresh;
    AtlasTerrainCollector() { this(System::nanoTime); }
    AtlasTerrainCollector(LongSupplier clock) { this.clock = clock; }
    void load(long key) {
        if (loaded.add(key)) loadedRevision++;
        refresh.remove(key); pending.add(key);
    }
    void unload(long key, Function<Long, AtlasSnapshot.Cell> sample, Consumer<AtlasSnapshot.Cell> captured) {
        // Every unload is sampled: an edit since the last refresh would otherwise only reach the map from the save.
        if (loaded.contains(key) || pending.contains(key) || refresh.contains(key)) {
            var tile = sample.apply(key);
            if (tile != null && accept(tile)) captured.accept(tile);
        }
        if (loaded.remove(key)) loadedRevision++;
        pending.remove(key); refresh.remove(key);
    }
    int tick(int x, int z, Function<Long, AtlasSnapshot.Cell> sample, Consumer<AtlasSnapshot.Cell> captured) {
        // A refresh cycle must finish before another starts. Never put already
        // captured neighbours ahead of the remaining first-capture work.
        if (++ticks % 200 == 0 && refresh.isEmpty())
            for (long key : loaded) if (!pending.contains(key)) refresh.add(key);
        pending.focus(x, z); refresh.focus(x, z);
        long deadline = clock.getAsLong() + BUDGET_NANOS;
        int count = 0;
        var retry = new ArrayList<Long>();
        while (count < PER_TICK && clock.getAsLong() < deadline) {
            // Reserve one in eight captures for edits even while exploring.
            boolean refreshing = !refresh.isEmpty() && (pending.isEmpty() || sinceRefresh >= 7);
            Long key = refreshing ? refresh.poll() : pending.poll();
            if (key == null) break;
            sinceRefresh = refreshing ? 0 : Math.min(7, sinceRefresh + 1);
            var tile = sample.apply(key);
            if (tile != null) { if (accept(tile)) captured.accept(tile); }
            else retry.add(key);
            count++;
        }
        // Retry not-yet-available chunks next tick, never spin on one this tick.
        refresh.addAll(retry);
        return count;
    }
    boolean accept(AtlasSnapshot.Cell tile) {
        long key = ChunkPos.pack(tile.x(), tile.z());
        var previous = history.get(key);
        if (previous != null && previous.sameTerrain(tile)) return false;
        history.remove(key); history.put(key, tile); revision++;
        while (history.size() > HISTORY_LIMIT) history.remove(history.keySet().iterator().next());
        return true;
    }
    List<AtlasSnapshot.Cell> cells() {
        if (cachedRevision != revision) { cached = List.copyOf(history.values()); cachedRevision = revision; }
        return cached;
    }
    long revision() { return revision; }
    Set<Long> loadedSnapshot() {
        if (cachedLoadedRevision != loadedRevision) {
            cachedLoaded = Set.copyOf(loaded); cachedLoadedRevision = loadedRevision;
        }
        return cachedLoaded;
    }
    long loadedRevision() { return loadedRevision; }
}
