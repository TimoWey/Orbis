package dev.orbis.debug;

/** Pure coordinate math, also used for the bounded survey (never world-sized arrays). */
public final class DebugMapMath {
    public static int sampleCoordinate(int min, int width, int index, int cells) {
        return min + (int) (((2L * index + 1) * width) / (2L * cells));
    }

    public static double unit(double value) { return value - Math.floor(value); }

    public static double[] globe(double u, double v, double yaw, double pitch) {
        double longitude = (u - .5) * Math.PI * 2 + yaw;
        double latitude = (.5 - v) * Math.PI;
        double x = Math.cos(latitude) * Math.sin(longitude);
        double y = Math.sin(latitude);
        double z = Math.cos(latitude) * Math.cos(longitude);
        return new double[] {x, y * Math.cos(pitch) - z * Math.sin(pitch),
                y * Math.sin(pitch) + z * Math.cos(pitch)};
    }

    public static double[] unproject(double x, double y, double yaw, double pitch) {
        double square = x * x + y * y;
        if (square > 1) return null;
        double z = Math.sqrt(1 - square);
        double originalY = y * Math.cos(pitch) + z * Math.sin(pitch);
        double originalZ = -y * Math.sin(pitch) + z * Math.cos(pitch);
        return new double[] {unit(.5 + (Math.atan2(x, originalZ) - yaw) / (2 * Math.PI)),
                Math.max(0, Math.min(1, .5 - Math.asin(Math.max(-1, Math.min(1, originalY))) / Math.PI))};
    }

    public static int biomeColor(String name) {
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
    private DebugMapMath() {}
}
