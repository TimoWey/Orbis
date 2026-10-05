package dev.orbis.debug;

import java.util.Arrays;
import net.minecraft.world.level.ChunkPos;

/** Bounded CPU raster. There are no per-cell GUI elements, GPU calls or world reads here. */
final class DebugMapRaster {
    static final int BACKGROUND = 0xff080f17;
    static final int LOADED = 0xff70dfbd;
    record View(int width, int height, double centerX, double centerZ, double scale,
                boolean globe, double yaw, double pitch, boolean chunks, boolean places) {}
    private final int width, height;
    private final int[] pixels;
    private double ratioX, ratioY;
    private View view;
    private DebugSnapshot data;

    DebugMapRaster(int width, int height) {
        this.width = width;
        this.height = height;
        pixels = new int[width * height];
    }
    int[] render(DebugSnapshot data, View view) {
        this.data = data;
        this.view = view;
        ratioX = width / (double) view.width;
        ratioY = height / (double) view.height;
        Arrays.fill(pixels, BACKGROUND);
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
            else cross(x(place.x()), y(place.z()), 0xffffd279);
        }
        return pixels;
    }
    private int x(double x) { return (int) Math.clamp((view.width * .5 + (x - view.centerX) * view.scale) * ratioX, -1000000, 1000000); }
    private int y(double z) { return (int) Math.clamp((view.height * .5 + (z - view.centerZ) * view.scale) * ratioY, -1000000, 1000000); }
    private void flat() {
        double step = data.span() / (double) DebugSurvey.GRID;
        for (var sample : data.surveyColors().entrySet()) {
            double x = data.minX() + (sample.getKey() % DebugSurvey.GRID) * step;
            double z = data.minZ() + (sample.getKey() / DebugSurvey.GRID) * step;
            fill(x(x), y(z), x(x + step), y(z + step), sample.getValue());
        }
        if (view.chunks && 16 * view.scale >= 1) {
            for (var cell : data.chunks()) {
                double x = cell.x() * 16.0, z = cell.z() * 16.0;
                fill(x(x), y(z), x(x + 16), y(z + 16), cell.color());
            }
            for (long key : data.loaded()) {
                double x = ChunkPos.getX(key) * 16.0, z = ChunkPos.getZ(key) * 16.0;
                if (16 * view.scale >= 6) outline(x(x), y(z), x(x + 16), y(z + 16), LOADED);
            }
        }
        if (data.wrapped()) outline(x(data.minX()), y(data.minZ()), x((double) data.minX() + data.span()),
                y((double) data.minZ() + data.span()), 0xffeac66c);
    }
    private void globe() {
        double radius = Math.max(8, Math.min(view.width, view.height) * .5 - 4) * view.scale;
        double sinPitch = Math.sin(view.pitch), cosPitch = Math.cos(view.pitch);
        // One inverse projection per texture pixel, only when the view or map changes.
        for (int py = 0; py < height; py++) {
            double yy = (view.height * .5 - (py + .5) / ratioY) / radius;
            for (int px = 0; px < width; px++) {
                double xx = ((px + .5) / ratioX - view.width * .5) / radius;
                double sq = xx * xx + yy * yy;
                if (sq > 1) continue;
                double zz = Math.sqrt(1 - sq);
                double originalY = yy * cosPitch + zz * sinPitch;
                double originalZ = -yy * sinPitch + zz * cosPitch;
                double u = DebugMapMath.unit(.5 + (Math.atan2(xx, originalZ) - view.yaw) / (2 * Math.PI));
                double v = .5 - Math.asin(Math.clamp(originalY, -1, 1)) / Math.PI;
                int ix = Math.clamp((int) (u * DebugSurvey.GRID), 0, DebugSurvey.GRID - 1);
                int iz = Math.clamp((int) (v * DebugSurvey.GRID), 0, DebugSurvey.GRID - 1);
                int color = data.surveyColors().getOrDefault(iz * DebugSurvey.GRID + ix, 0xff283b50);
                if (data.surveyColors().isEmpty() && (ix % 16 == 0 || iz % 16 == 0)) color = 0xff536779;
                if (view.chunks) {
                    int bx = (int) Math.floor(data.minX() + u * data.span());
                    int bz = (int) Math.floor(data.minZ() + v * data.span());
                    if (data.loaded().contains(ChunkPos.pack(bx >> 4, bz >> 4))) {
                        color = blend(color, LOADED);
                    }
                }
                pixels[py * width + px] = color;
            }
        }

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
        if (!view.globe) return new double[] {x(x), y(z)};
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
