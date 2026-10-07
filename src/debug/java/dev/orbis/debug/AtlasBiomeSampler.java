package dev.orbis.debug;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.RandomState;

/** Server-thread-only sampler: no world/chunk access; starts no new query after the caller's deadline. */
final class AtlasBiomeSampler {
    @FunctionalInterface
    interface Sink {
        /** Commit this index before returning, so a later resolver failure preserves completed work. */
        void accept(int index, int x, int y, int z, String biome, int color);
    }

    record Style(String id, int color) {}
    private final BiomeResolver resolver;
    private final int[] xs = new int[DebugSurvey.GRID], zs = new int[DebugSurvey.GRID], ys;
    private final Map<Holder<Biome>, Style> styles = new IdentityHashMap<>();
    private final LongSupplier clock;

    static AtlasBiomeSampler create(BiomeSource source, RandomState random,
            int minX, int minZ, int span, int[] heights) {
        // Minecraft's biome-search API uses this same resolver. Its density caches hold
        // the last coordinate per density function, not every atlas sample or any chunks.
        return new AtlasBiomeSampler(source.createCachingResolver(random), minX, minZ, span, heights, System::nanoTime);
    }

    AtlasBiomeSampler(BiomeResolver resolver, int minX, int minZ, int span, int[] heights, LongSupplier clock) {
        if (heights.length != DebugSurvey.LAYERS) throw new IllegalArgumentException("Expected four survey heights");
        this.resolver = resolver;
        this.ys = heights.clone();
        this.clock = clock;
        for (int i = 0; i < DebugSurvey.GRID; i++) {
            xs[i] = DebugMapMath.sampleCoordinate(minX, span, i, DebugSurvey.GRID);
            zs[i] = DebugMapMath.sampleCoordinate(minZ, span, i, DebugSurvey.GRID);
        }
    }

    /** Legacy full-grid traversal for parity/benchmark tests. Live searches use AtlasSearch and query(). */
    int run(int cursor, long deadline, BooleanSupplier active, Sink sink) {
        if (cursor < 0 || cursor > DebugSurvey.TOTAL) throw new IllegalArgumentException("Invalid survey cursor");
        while (cursor < DebugSurvey.TOTAL && active.getAsBoolean() && clock.getAsLong() < deadline) {
            int cell = cursor / DebugSurvey.LAYERS;
            int x = xs[cell % DebugSurvey.GRID], z = zs[cell / DebugSurvey.GRID];
            int y = ys[cursor % DebugSurvey.LAYERS];
            Style style = query(x, y, z);
            sink.accept(cursor, x, y, z, style.id, style.color);
            cursor++;
        }
        return cursor;
    }
    Style query(int x, int y, int z) {
        Holder<Biome> biome = resolver.getNoiseBiome(x >> 2, y >> 2, z >> 2);
        Style style = styles.get(biome);
        if (style == null) {
            String id = biome.unwrapKey().map(key -> key.identifier().toString()).orElse("unregistered");
            style = new Style(id, DebugMapMath.biomeColor(id));
            // Registered biome holders are stable. Bound this auxiliary cache even for
            // custom sources that manufacture a fresh holder on every invocation.
            if (styles.size() < 4096) styles.put(biome, style);
        }
        return style;
    }
}
