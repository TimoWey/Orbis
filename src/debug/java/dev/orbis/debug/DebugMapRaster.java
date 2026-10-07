package dev.orbis.debug;

import java.util.Arrays;
import net.minecraft.world.level.ChunkPos;

/** Bounded CPU raster. There are no per-cell GUI elements, GPU calls or world reads here. */
final class DebugMapRaster {
    static final int BACKGROUND = 0x00000000;
    static final int LOADED = 0xff70dfbd;
    record View(int width, int height, double centerX, double centerZ, double scale,
                boolean globe, double yaw, double pitch, boolean chunks, boolean places) {}
    private final int width, height;
    private final int[] pixels;
    private double ratioX, ratioY;
    private double leftWorld, rightWorld, topWorld, bottomWorld;
    private View view;
    private DebugSnapshot data;
    private int[] archived;
    private java.util.List<DebugSnapshot.Cell> indexedChunks;
    private final java.util.Map<Long, DebugSnapshot.Cell> terrain = new java.util.HashMap<>();
    private double[] globeU, globeV;
    private double projectionRadius = Double.NaN;
    private int projectionWidth, projectionHeight;

    DebugMapRaster(int width, int height) {
        this.width = width;
        this.height = height;
        pixels = new int[width * height];
    }
    int[] render(DebugSnapshot data, View view) {
        return render(data, view, null);
    }
    int[] render(DebugSnapshot data, View view, int[] archived) {
        this.data = data;
        this.view = view;
        this.archived = archived;
        if (indexedChunks != data.chunks()) {
            indexedChunks = data.chunks();
            terrain.clear();
            for (var cell : indexedChunks) terrain.put(ChunkPos.pack(cell.x(), cell.z()), cell);
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
        double step = data.span() / (double) DebugSurvey.GRID;
        int firstX = Math.clamp((int) Math.floor((leftWorld - data.minX()) / step), 0, DebugSurvey.GRID - 1);
        int lastX = Math.clamp((int) Math.floor((rightWorld - data.minX()) / step), 0, DebugSurvey.GRID - 1);
        int firstZ = Math.clamp((int) Math.floor((topWorld - data.minZ()) / step), 0, DebugSurvey.GRID - 1);
        int lastZ = Math.clamp((int) Math.floor((bottomWorld - data.minZ()) / step), 0, DebugSurvey.GRID - 1);
        for (int iz = firstZ; iz <= lastZ; iz++) for (int ix = firstX; ix <= lastX; ix++) {
            Integer color = data.surveyColors().get(iz * DebugSurvey.GRID + ix);
            if (color != null) worldFill(data.minX() + ix * step, data.minZ() + iz * step, step, color);
        }
        // Sample original block colors at visible pixel centers, regardless of zoom.
        if (archived != null) for (int i = 0; i < pixels.length; i++) if (archived[i] != 0) pixels[i] = archived[i];
        // Work stays bounded by screen coverage without replacing terrain with coarse averages.
        double densityX = view.scale * ratioX, densityY = view.scale * ratioY;
        for (var cell : data.chunks()) {
            double cx = cell.x() * 16.0, cz = cell.z() * 16.0;
            if (cx + 16 <= leftWorld || cx >= rightWorld || cz + 16 <= topWorld || cz >= bottomWorld) continue;
            if (data.wrapped() && (cx < data.minX() || cz < data.minZ()
                    || cx >= (double) data.minX() + data.span() || cz >= (double) data.minZ() + data.span())) continue;
            int left = Math.clamp((int) Math.ceil((cx - leftWorld) * densityX - .5), 0, width);
            int right = Math.clamp((int) Math.ceil((cx + 16 - leftWorld) * densityX - .5), 0, width);
            int top = Math.clamp((int) Math.ceil((cz - topWorld) * densityY - .5), 0, height);
            int bottom = Math.clamp((int) Math.ceil((cz + 16 - topWorld) * densityY - .5), 0, height);
            boolean live = data.loaded().contains(ChunkPos.pack(cell.x(), cell.z()));
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
            for (long key : data.loaded()) {
                double x = ChunkPos.getX(key) * 16.0, z = ChunkPos.getZ(key) * 16.0;
                if (16 * view.scale >= 6) outline(x(x), y(z), x(x + 16), y(z + 16), LOADED);
            }
        }
        if (data.wrapped()) outline(x(data.minX()), y(data.minZ()), x((double) data.minX() + data.span()),
                y((double) data.minZ() + data.span()), 0xffeac66c);
    }
    /** Exactly one canonical world rectangle, with no neighbouring copies. */
    private void worldFill(double bx, double bz, double size, int color) {
        if (data.wrapped() && (bx < data.minX() || bz < data.minZ()
                || bx >= (double) data.minX() + data.span() || bz >= (double) data.minZ() + data.span())) return;
        if (bx + size <= leftWorld || bx >= rightWorld || bz + size <= topWorld || bz >= bottomWorld) return;
        fill(x(bx), y(bz), x(bx + size), y(bz + size), color);
    }
    private void globe() {
        double radius = Math.max(8, Math.min(view.width, view.height) * .5 - 4) * view.scale;
        cacheProjection(radius);
        double originU = .5 - view.yaw / (2 * Math.PI), originV = .5 - view.pitch / Math.PI;
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int index = py * width + px;
                if (Double.isNaN(globeU[index])) continue;
                double u = DebugMapMath.unit(originU + globeU[index]);
                double v = DebugMapMath.unit(originV + globeV[index]);
                int ix = Math.clamp((int) (u * DebugSurvey.GRID), 0, DebugSurvey.GRID - 1);
                int iz = Math.clamp((int) (v * DebugSurvey.GRID), 0, DebugSurvey.GRID - 1);
                int color = data.surveyColors().getOrDefault(iz * DebugSurvey.GRID + ix, 0xff283b50);
                if (data.surveyColors().isEmpty() && (ix % 16 == 0 || iz % 16 == 0)) color = 0xff536779;
                if (archived != null && archived[index] != 0) color = archived[index];
                int bx = (int) Math.floor(data.minX() + u * data.span());
                int bz = (int) Math.floor(data.minZ() + v * data.span());
                var cell = terrain.get(ChunkPos.pack(bx >> 4, bz >> 4));
                if (cell != null && (archived == null || archived[index] == 0
                        || data.loaded().contains(ChunkPos.pack(bx >> 4, bz >> 4)))) color = cell.colorAt(bx, bz);
                if (view.chunks) {
                    if (data.loaded().contains(ChunkPos.pack(bx >> 4, bz >> 4))) {
                        color = blend(color, LOADED);
                    }
                }
                pixels[index] = color;
            }
        }

    }
    /** Rotation reuses the expensive inverse chart; only zoom or sizing changes it. */
    private void cacheProjection(double radius) {
        if (projectionRadius == radius && projectionWidth == view.width && projectionHeight == view.height) return;
        if (globeU == null) { globeU = new double[pixels.length]; globeV = new double[pixels.length]; }
        for (int py = 0; py < height; py++) {
            double yy = (view.height * .5 - (py + .5) / ratioY) / radius;
            for (int px = 0; px < width; px++) {
                int index = py * width + px;
                double xx = ((px + .5) / ratioX - view.width * .5) / radius;
                double square = xx * xx + yy * yy;
                if (square > 1) { globeU[index] = Double.NaN; continue; }
                double distance = Math.sqrt(square);
                double scale = distance == 0 ? 0 : Math.asin(distance) / (2 * Math.PI * distance);
                globeU[index] = xx * scale;
                globeV[index] = -yy * scale;
            }
        }
        projectionRadius = radius; projectionWidth = view.width; projectionHeight = view.height;
    }
    private void globePoint(double x, double z, int color, boolean place) {
        double[] xyz = DebugMapMath.globe((x - data.minX()) / data.span(), (z - data.minZ()) / data.span(), view.yaw, view.pitch);
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
        if (!view.globe) return new double[] {x(data.wrapped() ? DebugMapMath.canonical(x, data.minX(), data.span()) : x),
                y(data.wrapped() ? DebugMapMath.canonical(z, data.minZ(), data.span()) : z)};
        double[] p = DebugMapMath.globe((x - data.minX()) / data.span(), (z - data.minZ()) / data.span(), view.yaw, view.pitch);
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
