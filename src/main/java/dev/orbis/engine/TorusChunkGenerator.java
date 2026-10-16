package dev.orbis.engine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.orbis.WorldSize;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Vanilla noise generation on a square torus. The generator itself only carries the bounds; periodic
 * noise, folded lookups and wrapping are attached to the level by the engine mixins.
 */
public final class TorusChunkGenerator extends NoiseBasedChunkGenerator {
    /** A Nether as narrow as the smallest test world up to an Earth-sized Overworld; a bad save reports instead of crashing. */
    private static final Codec<Integer> WIDTH_CHUNKS = Codec.intRange(WorldSize.DEBUG_MIN_CHUNKS / 8, WorldSize.EARTH_CHUNKS);

    public static final MapCodec<TorusChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings),
            WIDTH_CHUNKS.fieldOf("width_chunks").forGetter(g -> g.torus.widthChunks()))
            .apply(i, i.stable(TorusChunkGenerator::new)));

    private final Torus torus;

    public TorusChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, int widthChunks) {
        super(biomeSource, settings);
        torus = Torus.centered(Math.multiplyExact(widthChunks, 16));
    }

    public Torus torus() { return torus; }

    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    /**
     * As vanilla, a chunk references every structure start within eight chunks whose box reaches it. A start
     * across the seam keeps its box in its own frame, so the box is compared with this chunk's image there.
     */
    @Override
    public void createReferences(WorldGenLevel level, StructureManager structures, ChunkAccess chunk) {
        ChunkPos centre = chunk.getPos();
        for (int x = centre.x() - 8; x <= centre.x() + 8; x++) {
            for (int z = centre.z() - 8; z <= centre.z() + 8; z++) {
                ChunkAccess source = level.getChunk(x, z);
                int shiftX = (source.getPos().x() - x) * 16, shiftZ = (source.getPos().z() - z) * 16;
                long reference = ChunkPos.pack(x, z);
                for (StructureStart start : source.getAllStarts().values()) {
                    if (start.isValid() && start.getBoundingBox().intersects(centre.getMinBlockX() + shiftX,
                            centre.getMinBlockZ() + shiftZ, centre.getMaxBlockX() + shiftX, centre.getMaxBlockZ() + shiftZ)) {
                        structures.addReferenceForStructure(start.getStructure(), reference, chunk);
                    }
                }
            }
        }
    }
}
