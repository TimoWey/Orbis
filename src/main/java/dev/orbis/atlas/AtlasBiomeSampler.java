package dev.orbis.atlas;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.RandomState;

/** Server-thread-only biome query and style cache; never accesses or loads chunks. */
final class AtlasBiomeSampler {
    record Style(String id, int color) {}
    private final BiomeResolver resolver;
    private final Map<Holder<Biome>, Style> styles = new IdentityHashMap<>();

    static AtlasBiomeSampler create(BiomeSource source, RandomState random) {
        // Minecraft's biome-search API uses this same resolver. Its density caches hold
        // the last coordinate per density function, not every atlas sample or any chunks.
        return new AtlasBiomeSampler(source.createCachingResolver(random));
    }

    AtlasBiomeSampler(BiomeResolver resolver) {
        this.resolver = resolver;
    }

    Style query(int x, int y, int z) {
        Holder<Biome> biome = resolver.getNoiseBiome(x >> 2, y >> 2, z >> 2);
        Style style = styles.get(biome);
        if (style == null) {
            String id = biome.unwrapKey().map(key -> key.identifier().toString()).orElse("unregistered");
            style = new Style(id, AtlasMapMath.biomeColor(id));
            // Registered biome holders are stable. Bound this auxiliary cache even for
            // custom sources that manufacture a fresh holder on every invocation.
            if (styles.size() < 4096) styles.put(biome, style);
        }
        return style;
    }
}
