package dev.orbis;

import com.mojang.serialization.Codec;
import com.toroidalworld.api.v1.option.GenerationOptions;
import com.toroidalworld.api.v1.option.WorldOption;
import com.toroidalworld.api.v1.shape.LoopSpans;
import com.toroidalworld.api.v1.shape.ShapeDimensions;
import com.toroidalworld.api.v1.shape.ShapeModule;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;

public final class OrbisShape {
    public static final WorldOption<Boolean> ENABLED = new WorldOption<>("orbis_enabled", 1000, Codec.BOOL, false);
    public static final ShapeModule<WorldSize> MODULE = ShapeModule.of(
            Identifier.fromNamespaceAndPath("orbis", "finite"), WorldSize.DEFAULT,
            OrbisShape::apply, OrbisShape::read);

    public static WorldDimensions apply(WorldDimensions dimensions, WorldSize size) {
        LoopSpans overworld = LoopSpans.ofWidth(size.overworldChunks());
        // An explicit NONE is intentional: it prevents the engine deriving a
        // toroidal End from the Overworld during dimension setup.
        WorldDimensions result = ShapeDimensions.withSpans(dimensions, overworld,
                overworld.scaledDown(8), LoopSpans.NONE,
                GenerationOptions.DEFAULT.with(ENABLED, true));
        requireValid(result);
        return result;
    }

    /** Validate the final registry too: datapacks can replace dimension types when a save is restored. */
    public static void validateRestored(Registry<LevelStem> dimensions) {
        var restored = new WorldDimensions(dimensions);
        if (owns(restored)) requireValid(restored);
    }

    private static void requireValid(WorldDimensions dimensions) {
        if (read(dimensions) == null) {
            throw new IllegalArgumentException("Invalid Orbis dimensions: expected aligned square Overworld/Nether bounds, "
                    + "coordinate scales 1/8 and an unwrapped End. Check dimension datapacks before loading this world.");
        }
    }

    public static boolean owns(WorldDimensions dimensions) {
        return ShapeDimensions.optionsOf(dimensions, LevelStem.OVERWORLD).get(ENABLED);
    }

    public static WorldSize read(WorldDimensions dimensions) {
        if (!owns(dimensions)) return null;
        var overworldStem = dimensions.get(LevelStem.OVERWORLD).orElse(null);
        var netherStem = dimensions.get(LevelStem.NETHER).orElse(null);
        if (overworldStem == null || netherStem == null
                || overworldStem.type().value().coordinateScale() != 1.0
                || netherStem.type().value().coordinateScale() != 8.0) return null;
        LoopSpans ow = ShapeDimensions.spansOf(dimensions, LevelStem.OVERWORLD);
        LoopSpans nether = ShapeDimensions.spansOf(dimensions, LevelStem.NETHER);
        LoopSpans end = ShapeDimensions.spansOf(dimensions, LevelStem.END);
        if (ow == null || !ow.isSquare() || nether == null || !nether.isSquare()) return null;
        if (end != null && (end.loops(Direction.Axis.X) || end.loops(Direction.Axis.Z))) return null;
        try {
            WorldSize size = new WorldSize(ow.chunkWidth(Direction.Axis.X));
            if (!ow.equals(LoopSpans.ofWidth(size.overworldChunks()))
                    || !nether.equals(LoopSpans.ofWidth(size.netherChunks()))) return null;
            return size;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
    private OrbisShape() {}
}
