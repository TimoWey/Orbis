package dev.orbis.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import dev.orbis.Orbis;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * One texture per map. The picture is rendered on a background thread, so the game never waits for it; the client
 * thread only copies a finished picture into the texture. A flat map is rendered with a margin around the view, and
 * panning inside it only moves the texture coordinates: the map follows the mouse exactly, at frame rate, and is
 * re-rendered around the new centre in the background.
 */
final class AtlasMapTexture implements AutoCloseable {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "Orbis atlas raster"); thread.setDaemon(true); return thread;
    });
    /** Extra width rendered on each side of a flat view, as a fraction of the view. */
    static final double MARGIN = .25;
    /** How often a settled picture still asks whether archived terrain has arrived since. */
    private static final long RECHECK_NANOS = 250_000_000L;
    /** How often a picture still waiting for archived terrain looks for it. */
    private static final long UNSETTLED_NANOS = 50_000_000L;
    private static int nextId;
    private final Identifier id = Identifier.fromNamespaceAndPath("orbis", "atlas_" + nextId++);
    /** Shortest time between two renders; the HUD globe needs fewer updates than a dragged map. */
    private final long intervalNanos;
    private DynamicTexture texture;
    private int width, height, uploads;
    private double updateMillis;
    private long lastStart;
    private CompletableFuture<Frame> pending;
    private Frame shown;
    private AtlasMapRaster.View requested;
    private long generation;
    // Only the raster thread touches these.
    private AtlasMapRaster raster;
    private NativeImage back;
    private final AtlasTerrainBackdrop backdrop = new AtlasTerrainBackdrop();

    /** A finished picture: what it shows and how its terrain layer stood when it was made. */
    private record Frame(AtlasMapKey key, AtlasMapRaster.View view, int width, int height, long generation,
                         long backdropVersion, boolean settled, NativeImage image, double millis) {}

    AtlasMapTexture() { this(0); }
    AtlasMapTexture(double maxUpdatesPerSecond) {
        intervalNanos = maxUpdatesPerSecond <= 0 ? 0 : (long) (1e9 / maxUpdatesPerSecond);
    }

    void draw(GuiGraphicsExtractor g, AtlasSnapshot data, AtlasMapRaster.View view, int x, int y) {
        requested = view;
        var target = target(data, view);
        int limit = view.globe() ? 768 : 1536;
        double factor = Math.min(Minecraft.getInstance().getWindow().getGuiScale(),
                limit / (double) Math.max(target.width(), target.height()));
        int w = Math.max(1, (int) (target.width() * factor)), h = Math.max(1, (int) (target.height() * factor));
        if (texture == null || w != width || h != height) {
            release();
            width = w; height = h;
            texture = new DynamicTexture(() -> "Orbis atlas", new NativeImage(w, h, false));
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
        if (pending != null && pending.isDone()) {
            Frame frame = null;
            try { frame = pending.join(); }
            catch (CompletionException failure) { Orbis.LOGGER.warn("Atlas map rendering failed", failure.getCause()); }
            pending = null;
            if (frame != null && frame.generation == generation && frame.width == width && frame.height == height) {
                if (frame.image != null) {
                    texture.getPixels().copyFrom(frame.image);
                    texture.upload();
                    uploads++;
                    updateMillis = frame.millis;
                }
                shown = frame;
            }
        }
        var key = AtlasMapKey.of(data, target);
        long now = System.nanoTime();
        boolean stale = shown == null || !shown.key.equals(key) || shown.width != width || shown.height != height;
        // A new view waits only for the update interval; a finished one looks again for archived terrain now and then.
        long wait = stale ? intervalNanos : shown.settled ? RECHECK_NANOS : UNSETTLED_NANOS;
        if (pending == null && (shown == null || now - lastStart >= wait)) {
            lastStart = now;
            pending = submit(data, target, key, w, h, generation, stale ? null : shown);
        }
        if (shown == null) return;
        if (!view.globe() && !shown.view.globe()) blitFlat(g, data, view, shown.view, x, y);
        // This overload takes opposite corners, not a width and height.
        else g.blit(id, x, y, x + view.width(), y + view.height(), 0, 1, 0, 1);
    }

    /**
     * What to render for a requested view. A globe is rendered as asked. A flat view keeps the centre of the picture
     * on screen while the view stays well inside its margin, so panning needs no new picture.
     */
    private AtlasMapRaster.View target(AtlasSnapshot data, AtlasMapRaster.View view) {
        if (view.globe()) return view;
        int mx = (int) Math.ceil(view.width() * MARGIN), my = (int) Math.ceil(view.height() * MARGIN);
        double cx = view.centerX(), cz = view.centerZ();
        if (shown != null && !shown.view.globe() && shown.view.scale() == view.scale()
                && shown.view.width() == view.width() + 2 * mx && shown.view.height() == view.height() + 2 * my
                && shown.view.chunks() == view.chunks() && shown.view.places() == view.places()) {
            double sx = nearest(data, view.centerX(), shown.view.centerX()), sz = nearest(data, view.centerZ(), shown.view.centerZ());
            if (Math.abs(sx - shown.view.centerX()) * view.scale() <= mx / 2.0
                    && Math.abs(sz - shown.view.centerZ()) * view.scale() <= my / 2.0) {
                cx = shown.view.centerX(); cz = shown.view.centerZ();
            }
        }
        return new AtlasMapRaster.View(view.width() + 2 * mx, view.height() + 2 * my, cx, cz, view.scale(),
                false, 0, 0, view.chunks(), view.places());
    }

    private static double nearest(AtlasSnapshot data, double value, double center) {
        return data.wrapped() ? AtlasMapMath.nearest(value, center, data.span()) : value;
    }

    /** Draws the part of the picture the requested view covers, at its exact place. */
    private void blitFlat(GuiGraphicsExtractor g, AtlasSnapshot data, AtlasMapRaster.View view, AtlasMapRaster.View picture, int x, int y) {
        double pictureWidth = picture.width() / picture.scale(), pictureHeight = picture.height() / picture.scale();
        double pictureLeft = picture.centerX() - pictureWidth / 2, pictureTop = picture.centerZ() - pictureHeight / 2;
        double viewWidth = view.width() / view.scale(), viewHeight = view.height() / view.scale();
        double left = nearest(data, view.centerX(), picture.centerX()) - viewWidth / 2;
        double top = nearest(data, view.centerZ(), picture.centerZ()) - viewHeight / 2;
        double u0 = (left - pictureLeft) / pictureWidth, u1 = (left + viewWidth - pictureLeft) / pictureWidth;
        double v0 = (top - pictureTop) / pictureHeight, v1 = (top + viewHeight - pictureTop) / pictureHeight;
        if (u0 >= 0 && u1 <= 1 && v0 >= 0 && v1 <= 1) {
            g.blit(id, x, y, x + view.width(), y + view.height(), (float) u0, (float) u1, (float) v0, (float) v1);
            return;
        }
        // Panned or zoomed out past the picture before the next one is ready: draw only the part it has.
        double cu0 = Math.max(0, u0), cu1 = Math.min(1, u1), cv0 = Math.max(0, v0), cv1 = Math.min(1, v1);
        if (cu0 >= cu1 || cv0 >= cv1) return;
        int x0 = x + (int) Math.round((cu0 - u0) / (u1 - u0) * view.width());
        int x1 = x + (int) Math.round((cu1 - u0) / (u1 - u0) * view.width());
        int y0 = y + (int) Math.round((cv0 - v0) / (v1 - v0) * view.height());
        int y1 = y + (int) Math.round((cv1 - v0) / (v1 - v0) * view.height());
        if (x0 < x1 && y0 < y1) g.blit(id, x0, y0, x1, y1, (float) cu0, (float) cu1, (float) cv0, (float) cv1);
    }

    /**
     * Renders on the raster thread into a native image the client thread then copies. With {@code current} set, only
     * archived terrain can have changed: the picture is rendered again only if it did.
     */
    private CompletableFuture<Frame> submit(AtlasSnapshot data, AtlasMapRaster.View view, AtlasMapKey key, int w, int h,
                                            long generation, Frame current) {
        return CompletableFuture.supplyAsync(() -> {
            if (generation != this.generation) return null;
            long start = System.nanoTime();
            int[] archived = backdrop.update(data, key.view(), w, h);
            boolean settled = backdrop.settled() || data.terrainArchive() == null;
            if (current != null && current.backdropVersion == backdrop.version())
                return new Frame(current.key, current.view, w, h, generation, current.backdropVersion, settled, null, current.millis);
            if (raster == null || raster.width() != w || raster.height() != h) raster = new AtlasMapRaster(w, h, true);
            if (back == null || back.getWidth() != w || back.getHeight() != h) {
                if (back != null) back.close();
                back = new NativeImage(w, h, false);
            }
            AtlasUpload.copy(raster.render(data, view, archived), back);
            return new Frame(key, view, w, h, generation, backdrop.version(), settled, back, (System.nanoTime() - start) / 1e6);
        }, WORKER);
    }

    int uploads() { return uploads; }
    /** The view the visible picture shows: the requested one for a flat map, which is cut to it exactly. */
    AtlasMapRaster.View displayedView() {
        if (shown == null) return null;
        return shown.view.globe() || requested == null || requested.globe() ? shown.view : requested;
    }
    double updateMillis() { return updateMillis; }
    String resolution() { return width + " x " + height; }

    private void release() {
        generation++;
        if (texture != null) Minecraft.getInstance().getTextureManager().release(id);
        texture = null; shown = null; pending = null;
    }

    @Override public void close() {
        release();
        WORKER.execute(() -> {
            backdrop.close();
            if (back != null) back.close();
            back = null; raster = null;
        });
    }
}
