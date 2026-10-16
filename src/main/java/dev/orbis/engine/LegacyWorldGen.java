package dev.orbis.engine;

import dev.orbis.Orbis;
import net.minecraft.nbt.CompoundTag;

/**
 * Opens worlds made by Orbis alphas from before the engine switch (such as 0.1.0-alpha.11), which and saved their
 * dimensions as {@code toroidal_world:toroidal} generators with a {@code wrapping} range per axis. Without this,
 * vanilla cannot read the world gen settings and quietly falls back to a default world with a random seed.
 *
 * <p>The rewrite happens on the loaded tag only; the file is rewritten in the new form the next time the
 * world saves. The seed, the bounds and every saved chunk stay as they are. Chunks that were never generated
 * come from this engine's noise, so they may not line up with old terrain they border.
 */
public final class LegacyWorldGen {
    static final String LEGACY_TOROIDAL = "toroidal_world:toroidal";
    private static final String LEGACY_NAMESPACE = "toroidal_world:";

    private LegacyWorldGen() {}

    /**
     * Rewrites the old generators inside a {@code world_gen_settings} data tag in place.
     *
     * @return whether anything was rewritten
     * @throws IllegalStateException for an old generator this engine cannot carry, so the world stops loading
     *         instead of opening with default settings
     */
    public static boolean migrate(CompoundTag data) {
        CompoundTag dimensions = data.getCompoundOrEmpty("dimensions");
        boolean changed = false;
        for (String dimension : dimensions.keySet()) {
            CompoundTag generator = dimensions.getCompoundOrEmpty(dimension).getCompoundOrEmpty("generator");
            String type = generator.getStringOr("type", "");
            if (type.equals(LEGACY_TOROIDAL)) {
                migrateGenerator(dimension, generator);
                changed = true;
            } else if (type.startsWith(LEGACY_NAMESPACE)) {
                throw new IllegalStateException("Orbis cannot open " + dimension + ": generator " + type
                        + " from an earlier engine is not supported");
            } else {
                // The earlier engine could also stamp its range onto other generators; this engine ignores it.
                changed |= generator.keySet().removeIf(key -> key.startsWith(LEGACY_NAMESPACE));
            }
        }
        return changed;
    }

    private static void migrateGenerator(String dimension, CompoundTag generator) {
        CompoundTag wrapping = generator.getCompoundOrEmpty("wrapping");
        CompoundTag x = wrapping.getCompoundOrEmpty("x"), z = wrapping.getCompoundOrEmpty("z");
        generator.remove("wrapping");
        generator.remove("orbis_enabled");
        if (x.isEmpty() && z.isEmpty()) {
            // The End never wrapped.
            generator.putString("type", "minecraft:noise");
            Orbis.LOGGER.info("Orbis: {} from an earlier alpha opens as an unwrapped dimension", dimension);
            return;
        }
        int width = widthOf(dimension, x);
        if (width != widthOf(dimension, z) || x.getIntOr("min_chunk", 0) != z.getIntOr("min_chunk", 0))
            throw new IllegalStateException("Orbis cannot open " + dimension + ": it is not square " + wrapping);
        generator.putString("type", "orbis:torus");
        generator.putInt("width_chunks", width);
        Orbis.LOGGER.info("Orbis: {} from an earlier alpha opens as a torus {} chunks wide", dimension, width);
    }

    /** Width of one axis; Orbis worlds were always centred on 0, which is all this engine supports. */
    private static int widthOf(String dimension, CompoundTag axis) {
        var minChunk = axis.getInt("min_chunk");
        var maxChunk = axis.getInt("max_chunk");
        if (minChunk.isEmpty() || maxChunk.isEmpty())
            throw new IllegalStateException("Orbis cannot open " + dimension + ": it wraps on one axis only");
        int min = minChunk.get(), max = maxChunk.get();
        int width = max - min;
        if (width <= 0 || min != -(width / 2))
            throw new IllegalStateException("Orbis cannot open " + dimension + ": bounds " + min + ".." + max
                    + " are not centred on 0");
        return width;
    }
}
