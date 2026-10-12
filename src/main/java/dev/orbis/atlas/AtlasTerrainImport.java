package dev.orbis.atlas;

import dev.orbis.Orbis;
import java.util.*;
import java.util.concurrent.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Bounded worker hand-off. Session ownership prevents late imports entering another world. */
final class AtlasTerrainImport implements AutoCloseable {
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, r -> {
        var thread = new Thread(r, "Orbis generated terrain"); thread.setDaemon(true); return thread;
    });
    record Tile(AtlasSnapshot.Cell cell, boolean distant) {}
    private final AtlasSavedTerrain saved;
    private AtlasDistantTerrain distant;
    private boolean dhDisabled;
    private final ArrayBlockingQueue<Tile> completed = new ArrayBlockingQueue<>(256);
    private CompletableFuture<Void> task;
    private boolean savedScanning, distantScanning;
    private long nextSavedScan, nextDistantScan;
    private int focusX = Integer.MIN_VALUE, focusZ = Integer.MIN_VALUE;
    private long nextNearbyScan;
    private Iterator<ChunkPos> nearby = Collections.emptyIterator();
    private volatile boolean closed;
    private AtlasTileArchive archive;
    private final LinkedHashMap<Long, AtlasSnapshot.Cell> live = new LinkedHashMap<>();
    volatile String error = "";

    AtlasTerrainImport(ServerLevel level) { saved = new AtlasSavedTerrain(level); }
    void archive(AtlasTileArchive archive) { this.archive = archive; }
    void capture(AtlasSnapshot.Cell tile) {
        synchronized (live) {
            if (closed) return;
            live.put(ChunkPos.pack(tile.x(), tile.z()), tile);
            if (live.size() > AtlasCache.CHUNK_LIMIT) live.remove(live.keySet().iterator().next());
        }
    }
    private void writeLive(int limit) throws java.io.IOException {
        for (int i = 0; i < limit; i++) {
            AtlasSnapshot.Cell tile;
            synchronized (live) {
                var it = live.values().iterator();
                if (!it.hasNext()) break;
                tile = it.next(); it.remove();
            }
            if (archive != null) archive.write(tile, 3);
        }
    }

    // Server thread only. All file/database reads and palette decoding happen on workers.
    void tick(ServerLevel level, int ticks) {
        if (closed || task != null && !task.isDone()) return;
        if (distant == null && !dhDisabled && ticks % 100 == 0 && FabricLoader.getInstance().isModLoaded("distanthorizons")) {
            try { distant = AtlasDistantTerrain.find(level); }
            catch (RuntimeException | LinkageError failure) {
                dhDisabled = true;
                error = "Distant Horizons import unavailable";
                Orbis.LOGGER.warn(error, failure);
            }
        }
        if (archive == null && completed.remainingCapacity() < 64) return;
        // Capture immutable coordinates here; workers never inspect live players/world state.
        var player = level.players().isEmpty() ? null : level.players().getFirst();
        int playerX = player == null ? 0 : player.getBlockX() >> 4;
        int playerZ = player == null ? 0 : player.getBlockZ() >> 4;
        task = CompletableFuture.runAsync(() -> {
            try {
                writeLive(64);
                long now = System.nanoTime();
                if (player != null && (Math.abs((long) playerX - focusX) >= 4
                        || Math.abs((long) playerZ - focusZ) >= 4 || now >= nextNearbyScan)) {
                    nearby = saved.nearby(playerX, playerZ, 12).iterator();
                    focusX = playerX; focusZ = playerZ;
                    nextNearbyScan = now + TimeUnit.SECONDS.toNanos(30);
                }
                if (!savedScanning && now >= nextSavedScan) {
                    saved.startScan(); savedScanning = true;
                    nextSavedScan = now + TimeUnit.SECONDS.toNanos(30);
                }
                if (distant != null && !distantScanning && now >= nextDistantScan) {
                    distant.startScan(); distantScanning = true;
                    nextDistantScan = now + TimeUnit.SECONDS.toNanos(30);
                }
                long savedDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(35);
                try (var reader = archive == null ? null : archive.reader()) {
                    for (int i = 0; i < 256 && (nearby.hasNext() || savedScanning) && !closed && System.nanoTime() < savedDeadline; i++) {
                        boolean priority = nearby.hasNext();
                        ChunkPos position = priority ? nearby.next() : saved.next();
                        if (position == null) { savedScanning = false; break; }
                        // Existing pixels are immediately usable. The background scan still refreshes saved data.
                        if (priority && reader != null && reader.get(position.x(), position.z()) != null) continue;
                        var tile = saved.read(position);
                        if (tile != null) store(new Tile(tile, false));
                    }
                }
                long distantDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(35);
                if (distant != null) for (int i = 0; i < 32 && !closed && System.nanoTime() < distantDeadline; i++) {
                    distantScanning = distant.readNext(tile -> store(new Tile(tile, true)));
                    if (!distantScanning) break;
                }
                if (!dhDisabled) error = "";
            } catch (RuntimeException | java.io.IOException | LinkageError failure) {
                saved.retryCurrentRegion();
                if (!closed) {
                    if (error.isEmpty()) Orbis.LOGGER.warn("Generated terrain import failed; will retry", failure);
                    error = "Generated terrain import failed; retrying";
                }
            } finally {
                if (closed) release();
            }
        }, WORKERS);
    }

    private void store(Tile tile) {
        try { if (archive != null) archive.write(tile.cell(), tile.distant()); }
        catch (java.io.IOException failure) { throw new CompletionException(failure); }
        if (archive == null) offer(tile);
    }

    private void offer(Tile tile) {
        try {
            while (!closed && !completed.offer(tile, 100, TimeUnit.MILLISECONDS)) { }
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
    Tile poll() { return completed.poll(); }
    CompletableFuture<Void> completion() { return task == null ? CompletableFuture.completedFuture(null) : task; }
    public void close() {
        closed = true; completed.clear();
        if (task == null || task.isDone()) task = CompletableFuture.runAsync(this::release, WORKERS);
    }
    private synchronized void release() {
        try { writeLive(Integer.MAX_VALUE); }
        catch (java.io.IOException failure) { Orbis.LOGGER.warn("Atlas final tile write failed", failure); }
        if (archive != null) archive.close();
        try { saved.close(); } catch (java.io.IOException failure) { Orbis.LOGGER.warn("Atlas region reader close failed", failure); }
        if (distant != null) { distant.close(); distant = null; }
    }
}
