package dev.orbis.atlas;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import net.minecraft.world.level.ChunkPos;

/** Bounded CPU raster. There are no per-cell GUI elements, GPU calls or world reads here. */
final class AtlasMapRaster {
    static final int BACKGROUND = 0x00000000;
    static final int LOADED = 0xff70dfbd;
    record View(int width, int height, double centerX, double centerZ, double scale,
                boolean globe, double yaw, double pitch, boolean chunks, boolean places) {}
    private final int width, height;
    private final int[] pixels;
    private double ratioX, ratioY;
    private double fogScale;
    private double leftWorld, rightWorld, topWorld, bottomWorld;
    private View view;
    private AtlasSnapshot data;
    private int[] archived;
    private java.util.List<AtlasSnapshot.Cell> indexedChunks;
    private java.util.Set<Long> indexedLoaded;
    // Primitive keys: the globe looks up every pixel, and boxed keys there are garbage at frame rate.
    private final Long2ObjectOpenHashMap<AtlasSnapshot.Cell> terrain = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet loaded = new LongOpenHashSet();
    private double[] globeU, globeV;
    // Per pixel lighting of the globe: brightness, highlight and haze inside the disk, halo alpha outside it.
    private float[] globeShade, globeShine, globeHaze;
    private static final int ATMOSPHERE = 0x9fd2ff;
    /** Rows per parallel globe task. */
    private static final int GLOBE_BAND = 16;
    // Light from the upper left, a little toward the viewer.
    private static final double LIGHT_X = -.5, LIGHT_Y = .6, LIGHT_Z = .62;
    private double projectionRadius = Double.NaN;
    private int projectionWidth, projectionHeight;

    // Shading is for the screen; plain rasters keep the original block colors.
    private final boolean lit;

    /** Closest globe zoom on small worlds, such as the debug world and small Nether maps. */
    static final double GLOBE_ZOOM_MAX = 12;
    /** Blocks across the shorter side of the view at the globe's closest zoom, where the world is big enough. */
    private static final double GLOBE_CLOSEST_BLOCKS = 256;

    /**
     * Closest globe zoom for a world {@code span} blocks wide. At a fixed limit a big world stays a small ball, so
     * the limit grows with the world until about {@link #GLOBE_CLOSEST_BLOCKS} blocks fill the view's shorter side.
     */
    static double maxGlobeZoom(int span) {
        // At zoom z the globe's centre shows span / (2 pi radius) blocks per pixel, and the radius is half the side.
        return Math.max(GLOBE_ZOOM_MAX, span / (Math.PI * GLOBE_CLOSEST_BLOCKS));
    }

    AtlasMapRaster(int width, int height) { this(width, height, false); }

    /** {@code lit}: the globe gets light, shadow and an atmosphere so it reads as a ball. */
    AtlasMapRaster(int width, int height, boolean lit) {
        this.lit = lit;
        this.width = width;
        this.height = height;
        pixels = new int[width * height];
    }
    int width() { return width; }
    int height() { return height; }
    int[] render(AtlasSnapshot data, View view) {
        return render(data, view, null);
    }
    int[] render(AtlasSnapshot data, View view, int[] archived) {
        this.data = data;
        this.view = view;
        this.archived = archived;
        // Fit whole decorative repeats around wrapped worlds, including small Nether maps.
        fogScale = data.wrapped() ? 4096.0 * Math.max(1, Math.round(data.span() / 4096.0)) / data.span() : 1;
        if (indexedChunks != data.chunks()) {
            indexedChunks = data.chunks();
            terrain.clear();
            for (var cell : indexedChunks) terrain.put(ChunkPos.pack(cell.x(), cell.z()), cell);
        }
        if (indexedLoaded != data.loaded()) {
            indexedLoaded = data.loaded();
            loaded.clear();
            for (long key : indexedLoaded) loaded.add(key);
        }
        ratioX = width / (double) view.width;
        ratioY = height / (double) view.height;
        leftWorld = view.centerX - view.width * .5 / view.scale;
        rightWorld = view.centerX + view.width * .5 / view.scale;
        topWorld = view.centerZ - view.height * .5 / view.scale;
        bottomWorld = view.centerZ + view.height * .5 / view.scale;
        Arrays.fill(pixels, view.globe ? 0 : BACKGROUND);
        if (view.globe) globe(); else flat();
        java.util.Set<Long> occupied = new java.util.HashSet<>();
        if (view.places) for (var place : data.places()) {
            // Collapse dense markers into one per small screen tile.
            double[] point = project(place.x(), place.z());
            if (point == null) continue;
            if (point[0] < 0 || point[0] >= width || point[1] < 0 || point[1] >= height) continue;
            int tile = Math.max(6, (int) (8 * ratioX));
            long key = ChunkPos.pack((int) point[0] / tile, (int) point[1] / tile);
            if (!occupied.add(key)) continue;
            if (view.globe) globePoint(place.x(), place.z(), 0xffffd279, true);
            else cross((int) point[0], (int) point[1], 0xffffd279);
        }
        return pixels;
    }
    private int x(double x) { return (int) Math.clamp((view.width * .5 + (x - view.centerX) * view.scale) * ratioX, -1000000, 1000000); }
    private int y(double z) { return (int) Math.clamp((view.height * .5 + (z - view.centerZ) * view.scale) * ratioY, -1000000, 1000000); }
    private void flat() {
        double densityX = view.scale * ratioX, densityY = view.scale * ratioY;
        int fogLevel = AtlasUnknownTerrain.level(fogScale * Math.max(1 / densityX, 1 / densityY));
        // A wrapped world continues past its edges, so the fog repeats with it (its pattern has the world's period).
        for (int py = 0; py < height; py++) {
            double z = topWorld + (py + .5) / densityY;
            for (int px = 0; px < width; px++)
                pixels[py * width + px] = AtlasUnknownTerrain.color((leftWorld + (px + .5) / densityX) * fogScale, z * fogScale, fogLevel);
        }
        // Sample original block colors at visible pixel centers, regardless of zoom.
        if (archived != null) for (int i = 0; i < pixels.length; i++) if (archived[i] != 0) pixels[i] = archived[i];
        // Work stays bounded by screen coverage without replacing terrain with coarse averages.
        // Views are at most one world wide, so on a wrapped world each cell shows in one of its neighbouring copies.
        // A zoomed-out picture reaches up to one and a half worlds past its centre.
        int copies = data.wrapped() ? 2 : 0;
        for (var cell : data.chunks()) for (int kx = -copies; kx <= copies; kx++) for (int kz = -copies; kz <= copies; kz++) {
            double cx = cell.x() * 16.0 + kx * (double) data.span(), cz = cell.z() * 16.0 + kz * (double) data.span();
            if (cx + 16 <= leftWorld || cx >= rightWorld || cz + 16 <= topWorld || cz >= bottomWorld) continue;
            if (data.wrapped() && (cell.x() * 16 < data.minX() || cell.z() * 16 < data.minZ()
                    || cell.x() * 16 >= data.minX() + data.span() || cell.z() * 16 >= data.minZ() + data.span())) continue;
            int left = Math.clamp((int) Math.ceil((cx - leftWorld) * densityX - .5), 0, width);
            int right = Math.clamp((int) Math.ceil((cx + 16 - leftWorld) * densityX - .5), 0, width);
            int top = Math.clamp((int) Math.ceil((cz - topWorld) * densityY - .5), 0, height);
            int bottom = Math.clamp((int) Math.ceil((cz + 16 - topWorld) * densityY - .5), 0, height);
            boolean live = loaded.contains(ChunkPos.pack(cell.x(), cell.z()));
            for (int py = top; py < bottom; py++) {
                int tz = Math.clamp((int) Math.floor(topWorld + (py + .5) / densityY - cz), 0, 15);
                for (int px = left; px < right; px++) {
                    if (!live && archived != null && archived[py * width + px] != 0) continue;
                    int tx = Math.clamp((int) Math.floor(leftWorld + (px + .5) / densityX - cx), 0, 15);
                    pixels[py * width + px] = cell.colorAt(tx, tz);
                }
            }
        }
        if (view.chunks && 16 * view.scale >= 1) {
            for (long key : data.loaded()) for (int kx = -copies; kx <= copies; kx++) for (int kz = -copies; kz <= copies; kz++) {
                double x = ChunkPos.getX(key) * 16.0 + kx * (double) data.span(), z = ChunkPos.getZ(key) * 16.0 + kz * (double) data.span();
                if (16 * view.scale >= 6) outline(x(x), y(z), x(x + 16), y(z + 16), LOADED);
            }
        }
    }
    private void globe() {
        double radius = Math.max(8, Math.min(view.width, view.height) * .5 - 4) * view.scale;
        cacheProjection(radius);
        double originU = .5 - view.yaw / (2 * Math.PI), originV = .5 - view.pitch / Math.PI;
        int fogLevel = AtlasUnknownTerrain.level(fogScale * data.span() / (2 * Math.PI * radius * Math.min(ratioX, ratioY)));
        // Rows are independent, so a picture as large as the screen still renders in a few milliseconds.
        java.util.stream.IntStream.range(0, (height + GLOBE_BAND - 1) / GLOBE_BAND).parallel().forEach(band -> {
            long lastKey = Long.MIN_VALUE;
            AtlasSnapshot.Cell lastCell = null;
            boolean lastLive = false;
            for (int py = band * GLOBE_BAND, end = Math.min(height, py + GLOBE_BAND); py < end; py++) {
                for (int px = 0; px < width; px++) {
                    int index = py * width + px;
                    if (Double.isNaN(globeU[index])) continue;
                    double u = AtlasMapMath.unit(originU + globeU[index]);
                    double v = AtlasMapMath.unit(originV + globeV[index]);
                    int bx = (int) Math.floor(data.minX() + u * data.span());
                    int bz = (int) Math.floor(data.minZ() + v * data.span());
                    // Neighbouring pixels mostly fall in the same chunk.
                    long key = ChunkPos.pack(bx >> 4, bz >> 4);
                    if (key != lastKey) { lastKey = key; lastCell = terrain.get(key); lastLive = loaded.contains(key); }
                    int color;
                    if (lastCell != null && (archived == null || archived[index] == 0 || lastLive)) color = lastCell.colorAt(bx, bz);
                    else if (archived != null && archived[index] != 0) color = archived[index];
                    else color = AtlasUnknownTerrain.color((data.minX() + u * data.span()) * fogScale,
                            (data.minZ() + v * data.span()) * fogScale, fogLevel);
                    if (view.chunks && lastLive) color = blend(color, LOADED);
                    pixels[index] = lit ? light(color, globeShade[index], globeShine[index], globeHaze[index]) : color;
                }
            }
        });
        // A thin glowing atmosphere around the disk.
        if (lit) for (int index = 0; index < pixels.length; index++)
            if (Double.isNaN(globeU[index]) && globeHaze[index] > 0)
                pixels[index] = Math.round(globeHaze[index] * 255) << 24 | ATMOSPHERE;
    }
    /** Lights a globe pixel: shade scales the colour, shine adds a white highlight, haze tints it toward the sky. */
    static int light(int color, float shade, float shine, float haze) {
        int r = channel((color >> 16 & 255) * shade, ATMOSPHERE >> 16 & 255, shine, haze);
        int g = channel((color >> 8 & 255) * shade, ATMOSPHERE >> 8 & 255, shine, haze);
        int b = channel((color & 255) * shade, ATMOSPHERE & 255, shine, haze);
        return 0xff000000 | r << 16 | g << 8 | b;
    }
    private static int channel(double value, int sky, float shine, float haze) {
        value = value * (1 - haze) + sky * haze;
        return (int) Math.clamp(Math.round(value + (255 - value) * shine), 0, 255);
    }
    /** Rotation reuses the expensive inverse chart; only zoom or sizing changes it. */
    private void cacheProjection(double radius) {
        if (projectionRadius == radius && projectionWidth == view.width && projectionHeight == view.height) return;
        if (globeU == null) {
            globeU = new double[pixels.length]; globeV = new double[pixels.length];
            globeShade = new float[pixels.length]; globeShine = new float[pixels.length]; globeHaze = new float[pixels.length];
        }
        double length = Math.sqrt(LIGHT_X * LIGHT_X + LIGHT_Y * LIGHT_Y + LIGHT_Z * LIGHT_Z);
        double lx = LIGHT_X / length, ly = LIGHT_Y / length, lz = LIGHT_Z / length;
        double half = Math.sqrt(lx * lx + ly * ly + (lz + 1) * (lz + 1));
        double hx = lx / half, hy = ly / half, hz = (lz + 1) / half;
        // The halo is a few screen pixels wide whatever the zoom.
        double halo = Math.max(3, radius * .07 * Math.min(ratioX, ratioY)) / (radius * Math.min(ratioX, ratioY));
        // Zooming the globe redraws this chart every frame, so its rows are shared out like the picture's.
        java.util.stream.IntStream.range(0, height).parallel().forEach(py -> {
            double yy = (view.height * .5 - (py + .5) / ratioY) / radius;
            for (int px = 0; px < width; px++) {
                int index = py * width + px;
                double xx = ((px + .5) / ratioX - view.width * .5) / radius;
                double square = xx * xx + yy * yy;
                double distance = Math.sqrt(square);
                globeShade[index] = 1; globeShine[index] = 0; globeHaze[index] = 0;
                if (square > 1) {
                    globeU[index] = Double.NaN;
                    double outside = (distance - 1) / halo;
                    if (outside < 1) globeHaze[index] = (float) (.6 * (1 - outside) * (1 - outside));
                    continue;
                }
                double nz = Math.sqrt(1 - square);
                double diffuse = Math.max(0, xx * lx + yy * ly + nz * lz);
                globeShade[index] = (float) (.5 + .62 * diffuse);
                globeShine[index] = (float) (.3 * Math.pow(Math.max(0, xx * hx + yy * hy + nz * hz), 30));
                globeHaze[index] = (float) (.4 * Math.pow(1 - nz, 2.2));
                double scale = distance == 0 ? 0 : Math.asin(distance) / (2 * Math.PI * distance);
                globeU[index] = xx * scale;
                globeV[index] = -yy * scale;
            }
        });
        projectionRadius = radius; projectionWidth = view.width; projectionHeight = view.height;
    }
    private void globePoint(double x, double z, int color, boolean place) {
        double[] xyz = AtlasMapMath.globe((x - data.minX()) / data.span(), (z - data.minZ()) / data.span(), view.yaw, view.pitch);
        if (xyz[2] <= 0) return;
        double radius = Math.max(8, Math.min(view.width, view.height) * .5 - 4) * view.scale;
        int px = (int) ((view.width * .5 + xyz[0] * radius) * ratioX);
        int py = (int) ((view.height * .5 - xyz[1] * radius) * ratioY);
        if (place) cross(px, py, color); else dot(px, py, color);
    }
    private static int blend(int base, int tint) {
        return 0xff000000 | (((base >> 16 & 255) * 4 + (tint >> 16 & 255)) / 5 << 16)
                | (((base >> 8 & 255) * 4 + (tint >> 8 & 255)) / 5 << 8)
                | ((base & 255) * 4 + (tint & 255)) / 5;
    }
    private double[] project(double x, double z) {
        if (!view.globe) return new double[] {x(data.wrapped() ? AtlasMapMath.nearest(x, view.centerX, data.span()) : x),
                y(data.wrapped() ? AtlasMapMath.nearest(z, view.centerZ, data.span()) : z)};
        double[] p = AtlasMapMath.globe((x - data.minX()) / data.span(), (z - data.minZ()) / data.span(), view.yaw, view.pitch);
        if (p[2] <= 0) return null;
        double r = Math.max(8, Math.min(view.width, view.height) * .5 - 4) * view.scale;
        return new double[] {(view.width * .5 + p[0] * r) * ratioX, (view.height * .5 - p[1] * r) * ratioY};
    }
    private void cross(int x, int y, int color) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        for (int d = -2; d <= 2; d++) { dot(x + d, y, color); dot(x, y + d, color); }
    }
    private void dot(int x, int y, int color) {
        if (x >= 0 && y >= 0 && x < width && y < height) pixels[y * width + x] = color;
    }
    private void fill(int left, int top, int right, int bottom, int color) {
        right = Math.clamp(Math.max(left + 1, right), 0, width);
        bottom = Math.clamp(Math.max(top + 1, bottom), 0, height);
        left = Math.max(0, left); top = Math.max(0, top);
        if (left >= right || top >= bottom) return;
        for (int y = top; y < bottom; y++) Arrays.fill(pixels, y * width + left, y * width + right, color);
    }
    private void outline(int left, int top, int right, int bottom, int color) {
        right = Math.max(left + 1, right); bottom = Math.max(top + 1, bottom);
        fill(left, top, right, top + 1, color);
        fill(left, bottom - 1, right, bottom, color);
        fill(left, top, left + 1, bottom, color);
        fill(right - 1, top, right, bottom, color);
    }
}
