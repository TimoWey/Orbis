package dev.orbis;

import dev.orbis.engine.TorusChunkGenerator;
import java.util.LinkedHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.jspecify.annotations.Nullable;

/** Turns a world's dimensions into an Orbis world of a chosen size, and reads the size back. */
public final class OrbisShape {
    /** Whether a world type can become an Orbis world: its Overworld and Nether must be noise-based. */
    public static boolean fits(WorldDimensions dimensions) {
        return generator(dimensions, LevelStem.OVERWORLD) instanceof NoiseBasedChunkGenerator
                && generator(dimensions, LevelStem.NETHER) instanceof NoiseBasedChunkGenerator;
    }

    /**
     * The Overworld and the Nether keep their noise settings and biomes, and their generators become
     * {@code orbis:torus} at 1:8. The End stays as it is.
     */
    public static WorldDimensions apply(WorldDimensions dimensions, WorldSize size) {
        if (!fits(dimensions)) throw new IllegalArgumentException("Orbis needs a noise-based Overworld and Nether");
        var stems = new LinkedHashMap<>(dimensions.dimensions());
        stems.put(LevelStem.OVERWORLD, torus(dimensions.get(LevelStem.OVERWORLD).orElseThrow(), size.overworldChunks()));
        stems.put(LevelStem.NETHER, torus(dimensions.get(LevelStem.NETHER).orElseThrow(), size.netherChunks()));
        WorldDimensions result = new WorldDimensions(stems);
        if (read(result) == null) throw new IllegalArgumentException("Invalid Orbis dimensions for " + size);
        return result;
    }

    /** The same dimensions without Orbis: torus generators become the noise generators they were made from. */
    public static WorldDimensions strip(WorldDimensions dimensions) {
        var stems = new LinkedHashMap<>(dimensions.dimensions());
        stems.replaceAll((key, stem) -> stem.generator() instanceof TorusChunkGenerator torus
                ? new LevelStem(stem.type(), new NoiseBasedChunkGenerator(torus.getBiomeSource(), torus.generatorSettings()))
                : stem);
        return new WorldDimensions(stems);
    }

    private static LevelStem torus(LevelStem stem, int widthChunks) {
        var noise = (NoiseBasedChunkGenerator) stem.generator();
        return new LevelStem(stem.type(), new TorusChunkGenerator(noise.getBiomeSource(), noise.generatorSettings(), widthChunks));
    }

    /** The size of an Orbis world: torus Overworld and Nether at exactly 1:8, End untouched; otherwise {@code null}. */
    public static @Nullable WorldSize read(WorldDimensions dimensions) {
        ChunkGenerator overworld = generator(dimensions, LevelStem.OVERWORLD), nether = generator(dimensions, LevelStem.NETHER);
        if (!(overworld instanceof TorusChunkGenerator ow) || !(nether instanceof TorusChunkGenerator ne)
                || generator(dimensions, LevelStem.END) instanceof TorusChunkGenerator) return null;
        var overworldType = dimensions.get(LevelStem.OVERWORLD).orElseThrow().type().value();
        var netherType = dimensions.get(LevelStem.NETHER).orElseThrow().type().value();
        if (overworldType.coordinateScale() != 1.0 || netherType.coordinateScale() != 8.0) return null;
        try {
            WorldSize size = new WorldSize(ow.torus().widthChunks());
            return ne.torus().equals(ow.torus().nether()) ? size : null;
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return null;
        }
    }

    private static @Nullable ChunkGenerator generator(WorldDimensions dimensions, ResourceKey<LevelStem> key) {
        return dimensions.get(key).map(LevelStem::generator).orElse(null);
    }

    private OrbisShape() {}
}
