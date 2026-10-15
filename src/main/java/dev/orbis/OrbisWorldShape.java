package dev.orbis;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.jspecify.annotations.Nullable;

/**
 * The World Shape chosen on the Create World screen: Minecraft's own world, or an Orbis world of a chosen size.
 * Re-creating a world starts from that world's shape.
 */
public final class OrbisWorldShape {
    private static boolean orbis;
    private static WorldSize size = WorldSize.DEFAULT;
    private static @Nullable Object worldType;

    public static boolean orbis() { return orbis; }
    public static void orbis(boolean chosen) { orbis = chosen; }
    public static WorldSize size() { return size; }
    public static void size(WorldSize chosen) { size = chosen; }

    /** A fresh Create World screen: Minecraft's own world at the default size, or the shape of the world re-created. */
    public static void reset(WorldDimensions existing) {
        WorldSize read = OrbisShape.read(existing);
        orbis = read != null;
        size = read != null ? read : WorldSize.DEFAULT;
        worldType = null;
    }

    /** Picking a world type that is already an Orbis world (such as {@code orbis:tiny}) picks Orbis at its size. */
    public static void follow(WorldCreationUiState state) {
        if (java.util.Objects.equals(state.getWorldType(), worldType)) return;
        boolean first = worldType == null;
        worldType = state.getWorldType();
        WorldSize read = OrbisShape.read(state.getSettings().selectedDimensions());
        if (read != null && !first) {
            orbis = true;
            size = read;
        }
    }

    /** The dimensions the world is created with. */
    public static WorldDimensions apply(WorldDimensions dimensions) {
        WorldDimensions plain = OrbisShape.strip(dimensions);
        return orbis && OrbisShape.fits(plain) ? OrbisShape.apply(plain, size) : plain;
    }

    private OrbisWorldShape() {}
}
