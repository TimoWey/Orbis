package dev.orbis.atlas;

import net.minecraft.client.Minecraft;

/** Lets the game test read the player map, which keeps its state package-private. */
public final class AtlasProbe {
    private AtlasProbe() {}

    /** The map frame shown now, as "wrapped minX minZ span", or null before the first snapshot. */
    public static String frame() {
        var data = PlayerAtlasClient.snapshot;
        return data == null ? null : data.wrapped() + " " + data.minX() + " " + data.minZ() + " " + data.span();
    }

    /** How many captured map cells lie outside the frame; folded maps have none. */
    public static long cellsOutside() {
        var data = PlayerAtlasClient.snapshot;
        if (data == null) return -1;
        int min = data.minX() >> 4, width = data.span() >> 4;
        return data.chunks().stream().filter(cell -> cell.x() < min || cell.x() >= min + width
                || cell.z() < (data.minZ() >> 4) || cell.z() >= (data.minZ() >> 4) + width).count();
    }

    /** The block coordinates the map label shows for the player now, as "x z", or null before the first snapshot. */
    public static String shownCoordinates(Minecraft client) {
        var data = PlayerAtlasClient.snapshot;
        if (data == null || !data.dimension().equals(client.level.dimension().identifier().toString())) return null;
        int[] shown = OrbisMinimap.shownBlock(data, client.player.getX(), client.player.getZ());
        return shown[0] + " " + shown[1];
    }

    public static void openMap(Minecraft client) { client.gui.setScreen(new PlayerAtlasScreen()); }

    /** The map's dimension and every chunk it has a picture of, as packed chunk positions, or null before the first snapshot. */
    public static java.util.Map.Entry<String, java.util.Set<Long>> cells() {
        var data = PlayerAtlasClient.snapshot;
        if (data == null) return null;
        var keys = new java.util.HashSet<Long>();
        for (var cell : data.chunks()) keys.add(net.minecraft.world.level.ChunkPos.pack(cell.x(), cell.z()));
        return java.util.Map.entry(data.dimension(), keys);
    }

    /**
     * The chunks out of {@code keys} the map has no picture of, neither in memory nor in its tile archive on disk, or
     * null before the first snapshot of {@code dimension}.
     */
    public static java.util.Set<Long> unknown(String dimension, java.util.Collection<Long> keys) throws java.io.IOException {
        var data = PlayerAtlasClient.snapshot;
        if (data == null || !data.dimension().equals(dimension)) return null;
        var known = new java.util.HashSet<Long>();
        for (var cell : data.chunks()) known.add(net.minecraft.world.level.ChunkPos.pack(cell.x(), cell.z()));
        var missing = new java.util.TreeSet<Long>();
        var archive = data.terrainArchive();
        try (var reader = archive == null ? null : archive.reader()) {
            for (long key : keys) {
                if (known.contains(key)) continue;
                int x = net.minecraft.world.level.ChunkPos.getX(key), z = net.minecraft.world.level.ChunkPos.getZ(key);
                if (reader == null || reader.get(x, z) == null) missing.add(key);
            }
        }
        return missing;
    }

    /** How many blocks across the flat map shows in a dimension. */
    public static double zoom(String dimension) { return AtlasPreferences.zoom(dimension, false); }

    /** Sets what the map shows when next opened in a dimension: the globe or the flat map, {@code blocks} across. */
    public static void prepare(String dimension, boolean globe, double blocks) {
        AtlasPreferences.setGlobe(dimension, globe);
        if (!globe) AtlasPreferences.zoom(dimension, false, blocks);
    }

    /** Whether the map is the screen shown now. */
    public static boolean mapOpen(Minecraft client) { return client.gui.screen() instanceof PlayerAtlasScreen; }

    /** The key that opens the map, as the player bound it. */
    public static net.minecraft.client.KeyMapping mapKey() { return PlayerAtlasClient.mapKey; }
}
