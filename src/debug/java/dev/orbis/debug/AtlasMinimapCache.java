package dev.orbis.debug;

import dev.orbis.Orbis;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.*;

/** A padded, block-resolution image. Camera movement only changes UVs on the client thread. */
final class AtlasMinimapCache implements AutoCloseable {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "Orbis minimap tiles"); thread.setDaemon(true); return thread;
    });
    record Window(int x, int z, int size) {
        static Window around(double x, double z, double span) {
            int size = ((int) Math.ceil(span / 16) + 8) * 16;
            return new Window((int) Math.floor((x + 32) / 64) * 64 - size / 2,
                    (int) Math.floor((z + 32) / 64) * 64 - size / 2, size);
        }
        boolean contains(double cx, double cz, double span, int margin) {
            return cx - span / 2 >= x + margin && cz - span / 2 >= z + margin
                    && cx + span / 2 <= x + size - margin && cz + span / 2 <= z + size - margin;
        }
        float u(double block) { return (float) ((block - x) / size); }
        float v(double block) { return (float) ((block - z) / size); }
        DebugMapRaster.View view() {
            return new DebugMapRaster.View(size, size, x + size / 2.0, z + size / 2.0,
                    1, false, 0, 0, false, false);
        }
    }
    private record Session(AtlasTileArchive archive, String dimension, long seed, int x, int z, int span, boolean wrapped) {
        static Session of(DebugSnapshot data) {
            return new Session(data.terrainArchive(), data.dimension(), data.seed(), data.minX(), data.minZ(), data.span(), data.wrapped());
        }
    }
    private record Target(Session session, Window window, long generation) {}
    private record Key(Target target, long terrain, long loaded, long archived) {}
    record Frame(Window window, int[] pixels) {}
    private record Result(Key key, Frame frame) {}
    private final Executor executor;
    private volatile Target wanted;
    private CompletableFuture<Result> pending;
    private Result ready;
    private long generation, retryAt;

    AtlasMinimapCache() { this(WORKER); }
    AtlasMinimapCache(Executor executor) { this.executor = executor; }

    Frame update(DebugSnapshot data, double x, double z, double span) {
        var session = Session.of(data);
        if (session.archive != null && session.archive.closed()) { close(); return null; }
        if (wanted == null || !wanted.session.equals(session) || !wanted.window.contains(x, z, span, 16)) {
            wanted = new Target(session, Window.around(x, z, span), generation);
            retryAt = 0;
        }
        if (pending != null && pending.isDone()) {
            try {
                Result result = pending.join();
                if (result != null && result.key.target.equals(wanted)) ready = result;
            } catch (CompletionException failure) {
                Orbis.LOGGER.warn("Minimap tile preparation failed; will retry", failure.getCause());
                retryAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            }
            pending = null;
        }
        Target target = wanted;
        Window window = target.window;
        long revision = session.archive == null ? 0 : session.archive.revision(window.x, window.z, window.size);
        var key = new Key(target, data.layers().terrain(), data.layers().loaded(), revision);
        if (pending == null && (ready == null || !ready.key.equals(key)) && System.nanoTime() >= retryAt) {
            Result previous = ready;
            pending = CompletableFuture.supplyAsync(() -> {
                try {
                    if (!target.equals(wanted)) return null;
                    int[] archived = session.archive == null ? null : AtlasTerrainBackdrop.render(session.archive, data,
                            window.view(), window.size, window.size, () -> !target.equals(wanted) || session.archive.closed());
                    if (!target.equals(wanted) || session.archive != null && archived == null) return null;
                    int[] pixels = new DebugMapRaster(window.size, window.size).render(data, window.view(), archived);
                    // Changes elsewhere may replace the snapshot but must not cause an identical GPU upload.
                    Frame frame = previous != null && previous.key.target.equals(target)
                            && Arrays.equals(previous.frame.pixels, pixels) ? previous.frame : new Frame(window, pixels);
                    return new Result(key, frame);
                } catch (IOException failure) { throw new CompletionException(failure); }
            }, executor);
        }
        return ready != null && ready.key.target.session.equals(session) && ready.key.target.generation == generation
                && ready.frame.window.contains(x, z, span, 0) ? ready.frame : null;
    }

    @Override public void close() { generation++; wanted = null; ready = null; retryAt = 0; }
}
