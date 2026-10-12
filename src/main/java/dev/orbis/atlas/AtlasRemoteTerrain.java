package dev.orbis.atlas;

import dev.orbis.Orbis;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.concurrent.*;
import net.minecraft.world.level.ChunkPos;

/**
 * Persistent terrain for one remote server world and dimension, keyed by the id the server publishes.
 * Captures arrive on the client thread; all file I/O happens on one worker.
 */
final class AtlasRemoteTerrain implements AutoCloseable {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "Orbis server atlas"); thread.setDaemon(true); return thread;
    });
    // Bump only when stored colors become incompatible; mod updates keep the explored map.
    private static final String FORMAT = "remote-v1";
    private final AtlasTileArchive archive;
    private final LinkedHashMap<Long, AtlasSnapshot.Cell> pending = new LinkedHashMap<>();
    private CompletableFuture<Void> task = CompletableFuture.completedFuture(null);
    private boolean closed;
    private volatile boolean failed;

    AtlasRemoteTerrain(Path directory) { archive = new AtlasTileArchive(directory); }

    static Path directory(Path base, String world, String dimension, int minX, int minZ, int span) {
        return base.resolve("servers").resolve(AtlasCache.hash(world)).resolve(AtlasCache.hash(dimension))
                .resolve(AtlasCache.hash(FORMAT + "|" + minX + "|" + minZ + "|" + span));
    }

    AtlasTileArchive archive() { return archive; }

    void capture(AtlasSnapshot.Cell tile) {
        if (closed || failed) return;
        long key = ChunkPos.pack(tile.x(), tile.z());
        synchronized (pending) {
            pending.remove(key); pending.put(key, tile);
            if (pending.size() > AtlasCache.CHUNK_LIMIT) pending.remove(pending.keySet().iterator().next());
        }
    }

    /** Client thread: hands queued captures to the worker without waiting for disk. */
    void tick() {
        if (closed || failed || !task.isDone()) return;
        synchronized (pending) { if (pending.isEmpty()) return; }
        task = CompletableFuture.runAsync(() -> write(256), IO);
    }

    CompletableFuture<Void> completion() { return task; }

    private void write(int limit) {
        try {
            for (int i = 0; i < limit; i++) {
                AtlasSnapshot.Cell tile;
                synchronized (pending) {
                    var it = pending.values().iterator();
                    if (!it.hasNext()) return;
                    tile = it.next(); it.remove();
                }
                archive.write(tile, 3);
            }
        } catch (IOException | RuntimeException failure) {
            failed = true;
            Orbis.LOGGER.warn("Server atlas archive unavailable; explored terrain stays in memory", failure);
        }
    }

    /** Flushes remaining captures, then releases the archive. Never blocks the caller. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        task = task.handle((ignored, failure) -> null).thenRunAsync(() -> {
            if (!failed) write(Integer.MAX_VALUE);
            archive.close();
        }, IO);
    }
}
