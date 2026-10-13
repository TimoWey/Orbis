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

    public static void openMap(Minecraft client) { client.gui.setScreen(new PlayerAtlasScreen()); }
}
