package dev.orbis.atlas;

import dev.orbis.Orbis;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Builds a screen-sized layer from archived full-resolution tiles without render-thread I/O. */
final class AtlasTerrainBackdrop implements AutoCloseable {
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, r -> {
        var t = new Thread(r, "Orbis terrain projection"); t.setDaemon(true); return t;
    });
    private record Key(AtlasTileArchive archive, AtlasMapRaster.View view, int width, int height) {}
    private record Result(Key key, long revision, int[] pixels) {}
    private volatile Key wanted;
    private CompletableFuture<Result> pending;
    private Result ready;
    private long version, nextStart;
    private boolean failed;

    int[] update(AtlasSnapshot data, AtlasMapRaster.View view, int width, int height) {
        var archive = data.terrainArchive();
        if (archive == null || archive.closed()) { if (wanted != null || ready != null) close(); return null; }
        var key = new Key(archive, view, width, height);
        if (!key.equals(wanted)) { wanted = key; version++; }
        if (pending != null && pending.isDone()) {
            try {
                var result = pending.join();
                if (result != null && result.key.equals(key)) { ready = result; version++; }
                failed = false;
            } catch (CompletionException failure) {
                if (!failed) Orbis.LOGGER.warn("Atlas archived terrain projection failed", failure.getCause());
                failed = true;
            }
            pending = null;
        }
        if (pending == null && (ready == null || !ready.key.equals(key) || System.nanoTime() >= nextStart)
                && (ready == null || !ready.key.equals(key) || ready.revision != archive.revision())) {
            long revision = archive.revision();
            nextStart = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(33);
            pending = CompletableFuture.supplyAsync(() -> {
                try {
                    int[] pixels = render(archive, data, view, width, height, () -> !key.equals(wanted) || archive.closed());
                    return pixels == null ? null : new Result(key, revision, pixels);
                } catch (IOException failure) { throw new CompletionException(failure); }
            }, WORKERS);
        }
        if (ready != null && ready.key.equals(key)) return ready.pixels;
        // Motion/reopening samples warm original tiles immediately. Missing regions load on workers.
        try (var reader = archive.residentReader()) {
            return render(reader, data, view, width, height, () -> false);
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    long version() { return version; }
    public void close() { wanted = null; ready = null; version++; }

    static int[] render(AtlasTileArchive archive, AtlasSnapshot data, AtlasMapRaster.View view,
            int width, int height, BooleanSupplier cancelled) throws IOException {
        try (var reader = archive.reader()) {
            return render(reader, data, view, width, height, cancelled);
        }
    }

    private static int[] render(AtlasTileArchive.Reader reader, AtlasSnapshot data, AtlasMapRaster.View view,
            int width, int height, BooleanSupplier cancelled) throws IOException {
        int[] pixels = new int[width * height];
        double ratioX = width / (double) view.width(), ratioY = height / (double) view.height();
        double radius = Math.max(8, Math.min(view.width(), view.height()) * .5 - 4) * view.scale();
        {
            for (int py = 0; py < height; py++) {
                if (cancelled.getAsBoolean()) return null;
                for (int px = 0; px < width; px++) {
                    double x, z;
                    if (view.globe()) {
                        double[] uv = AtlasMapMath.unproject(((px + .5) / ratioX - view.width() * .5) / radius,
                                (view.height() * .5 - (py + .5) / ratioY) / radius, view.yaw(), view.pitch());
                        if (uv == null) continue;
                        x = data.minX() + uv[0] * data.span(); z = data.minZ() + uv[1] * data.span();
                    } else {
                        x = view.centerX() + ((px + .5) / ratioX - view.width() * .5) / view.scale();
                        z = view.centerZ() + ((py + .5) / ratioY - view.height() * .5) / view.scale();
                        // Past a seam the archive is read at the same place in the world.
                        if (data.wrapped()) {
                            x = AtlasMapMath.canonical(x, data.minX(), data.span());
                            z = AtlasMapMath.canonical(z, data.minZ(), data.span());
                        }
                    }
                    int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
                    var tile = reader.get(bx >> 4, bz >> 4);
                    if (tile != null) pixels[py * width + px] = tile.colorAt(bx, bz);
                }
            }
        }
        return pixels;
    }
}
