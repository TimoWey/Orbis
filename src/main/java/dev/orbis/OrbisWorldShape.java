package dev.orbis;

import net.minecraft.world.level.levelgen.WorldDimensions;

/**
 * The World Shape chosen on the Create World screen: Minecraft's own world, or an Orbis world of a chosen size.
 * Re-creating a world starts from that world's shape.
 */
public final class OrbisWorldShape {
    private static boolean orbis;
    private static WorldSize size = WorldSize.DEFAULT;

    public static boolean orbis() { return orbis; }
    public static void orbis(boolean chosen) { orbis = chosen; }
    public static WorldSize size() { return size; }
    public static void size(WorldSize chosen) { size = chosen; }

    /** A fresh Create World screen: Minecraft's own world at the default size, or the shape of the world re-created. */
    public static void reset(WorldDimensions existing) {
        WorldSize read = OrbisShape.read(existing);
        orbis = read != null;
        size = read != null ? read : WorldSize.DEFAULT;
    }

    /** The dimensions the world is created with. */
    public static WorldDimensions apply(WorldDimensions dimensions) {
        WorldDimensions plain = OrbisShape.strip(dimensions);
        return orbis && OrbisShape.fits(plain) ? OrbisShape.apply(plain, size) : plain;
    }

    private OrbisWorldShape() {}
}
