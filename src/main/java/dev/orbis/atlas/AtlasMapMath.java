package dev.orbis.atlas;

/** Pure coordinate math, also used for the bounded survey (never world-sized arrays). */
public final class AtlasMapMath {
    public static int sampleCoordinate(int min, int width, int index, int cells) {
        return min + (int) (((2L * index + 1) * width) / (2L * cells));
    }

    public static double unit(double value) { return value - Math.floor(value); }

    public static double[] globe(double u, double v, double yaw, double pitch) {
        // Azimuthal, view-centred chart of the torus. Both axes have the same
        // local scale. The cut locus is hidden behind the visible hemisphere.
        double dx = unit(u - .5 + yaw / (2 * Math.PI) + .5) - .5;
        double dz = unit(v - .5 + pitch / Math.PI + .5) - .5;
        double distance = Math.hypot(dx, dz);
        if (distance == 0) return new double[] {0, 0, 1};
        double angle = Math.min(Math.PI, distance * 2 * Math.PI);
        double scale = Math.sin(angle) / distance;
        return new double[] {dx * scale, -dz * scale, Math.cos(angle)};
    }

    public static double[] unproject(double x, double y, double yaw, double pitch) {
        double square = x * x + y * y;
        if (square > 1) return null;
        double radius = Math.sqrt(square);
        double scale = radius == 0 ? 0 : Math.asin(radius) / (2 * Math.PI * radius);
        return new double[] {unit(.5 - yaw / (2 * Math.PI) + x * scale),
                unit(.5 - pitch / Math.PI - y * scale)};
    }

    static double canonical(double value, int min, int span) { return min + unit((value - min) / span) * span; }
    /** The copy of {@code value} on a wrapped axis that lies closest to {@code center}. */
    static double nearest(double value, double center, int span) { return center + (unit((value - center) / span + .5) - .5) * span; }

    public static int biomeColor(String name) {
        // Check these before generic forest/desert matching so Nether biomes stay distinguishable.
        switch (name) {
            case "minecraft:nether_wastes": return 0xff914c47;
            case "minecraft:soul_sand_valley": return 0xff917761;
            case "minecraft:crimson_forest": return 0xffad354d;
            case "minecraft:warped_forest": return 0xff318d83;
            case "minecraft:basalt_deltas": return 0xff616478;
            default: break;
        }
        if (name.contains("ocean") || name.contains("river")) return 0xff397fbd;
        if (name.contains("snow") || name.contains("frozen") || name.contains("ice")) return 0xffc9e6ed;
        if (name.contains("desert") || name.contains("beach")) return 0xffd4bb70;
        if (name.contains("badlands")) return 0xffbb704d;
        if (name.contains("forest") || name.contains("jungle") || name.contains("taiga")) return 0xff397d52;
        if (name.contains("nether") || name.contains("crimson")) return 0xffa34a4a;
        if (name.contains("warped")) return 0xff36968f;
        if (name.contains("end")) return 0xffb6aa77;
        int hash = name.hashCode();
        return 0xff000000 | (80 + (hash & 63)) << 16 | (112 + ((hash >>> 6) & 63)) << 8 | (72 + ((hash >>> 12) & 63));
    }
    private AtlasMapMath() {}
}
