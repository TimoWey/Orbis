package dev.orbis.atlas;

import dev.orbis.Orbis;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/**
 * Builds a screen-sized layer from archived full-resolution tiles without render-thread I/O. Not thread-safe: one
 * atlas texture calls it from its own raster thread.
 */
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
    private int[] scratch;
    private boolean failed;

    int[] update(AtlasSnapshot data, AtlasMapRaster.View view, int width, int height) {
        var archive = data.terrainArchive();
        if (archive == null || archive.closed()) { if (wanted != null || ready != null) close(); return null; }
        var key = new Key(archive, view, width, height);
        if (!key.equals(wanted)) { wanted = key; version++; }
        if (pending != null && pending.isDone()) {
            try {
                var result = pending.join();
                if (result != null && result.key.equals(key)) {
                    // Writes elsewhere in the shown regions often leave the visible pixels unchanged.
                    if (ready == null || !ready.key.equals(key) || !java.util.Arrays.equals(ready.pixels, result.pixels)) version++;
                    ready = result;
                }
                failed = false;
            } catch (CompletionException failure) {
                if (!failed) Orbis.LOGGER.warn("Atlas archived terrain projection failed", failure.getCause());
                failed = true;
            }
            pending = null;
        }
        long revision = revision(archive, data, view);
        if (pending == null && (ready == null || !ready.key.equals(key) || System.nanoTime() >= nextStart)
                && (ready == null || !ready.key.equals(key) || ready.revision != revision)) {
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
        if (scratch == null || scratch.length != width * height) scratch = new int[width * height];
        else java.util.Arrays.fill(scratch, 0);
        try (var reader = archive.residentReader()) {
            return render(reader, data, view, width, height, () -> false, scratch);
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    long version() { return version; }

    /** Flat views only follow writes in the regions they show; the globe and views across a seam use the global revision. */
    static long revision(AtlasTileArchive archive, AtlasSnapshot data, AtlasMapRaster.View view) {
        if (view.globe()) return archive.revision();
        double halfX = view.width() * .5 / view.scale(), halfZ = view.height() * .5 / view.scale();
        double minX = Math.floor(view.centerX() - halfX) - 1, maxX = Math.floor(view.centerX() + halfX) + 1;
        double minZ = Math.floor(view.centerZ() - halfZ) - 1, maxZ = Math.floor(view.centerZ() + halfZ) + 1;
        if (data.wrapped() && (minX < data.minX() || minZ < data.minZ()
                || maxX >= (double) data.minX() + data.span() || maxZ >= (double) data.minZ() + data.span()))
            return archive.revision();
        if (!(minX >= Integer.MIN_VALUE && minZ >= Integer.MIN_VALUE && maxX <= Integer.MAX_VALUE && maxZ <= Integer.MAX_VALUE))
            return archive.revision();
        // Very wide views would scan thousands of regions per frame.
        if ((((long) maxX >> 9) - ((long) minX >> 9) + 1) * (((long) maxZ >> 9) - ((long) minZ >> 9) + 1) > 4096)
            return archive.revision();
        return archive.revision((int) minX, (int) minZ, (int) maxX, (int) maxZ);
    }
    /** Whether the last {@link #update} returned the finished layer for its view, with nothing more on its way. */
    boolean settled() { return pending == null && ready != null && ready.key.equals(wanted); }
    public void close() { wanted = null; ready = null; version++; }

    static int[] render(AtlasTileArchive archive, AtlasSnapshot data, AtlasMapRaster.View view,
            int width, int height, BooleanSupplier cancelled) throws IOException {
        try (var reader = archive.reader()) {
            return render(reader, data, view, width, height, cancelled, new int[width * height]);
        }
    }

    private static int[] render(AtlasTileArchive.Reader reader, AtlasSnapshot data, AtlasMapRaster.View view,
            int width, int height, BooleanSupplier cancelled, int[] pixels) throws IOException {
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
