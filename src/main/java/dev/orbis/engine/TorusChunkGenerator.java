package dev.orbis.engine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/**
 * Vanilla noise generation on a square torus. The generator itself only carries the bounds; periodic
 * noise, folded lookups and wrapping are attached to the level by the engine mixins.
 */
public final class TorusChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<TorusChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings),
            Codec.INT.fieldOf("width_chunks").forGetter(g -> g.torus.widthChunks()))
            .apply(i, i.stable(TorusChunkGenerator::new)));

    private final Torus torus;

    public TorusChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, int widthChunks) {
        super(biomeSource, settings);
        torus = Torus.centered(Math.multiplyExact(widthChunks, 16));
    }

    public Torus torus() { return torus; }

    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }
}
