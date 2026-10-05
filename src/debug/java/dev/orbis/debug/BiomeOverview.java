package dev.orbis.debug;

import dev.orbis.WorldSize;
import java.util.Comparator;
import java.util.List;

/** Coverage means distinct dimension candidates found, never number of hits or world area. */
final class BiomeOverview {
    enum Filter { ALL, UNCONFIRMED, FOUND }
    enum Sort { NAME, HITS }
    record Counts(int total, int found, int observed, int sampled) {
        int unknown() { return total - found; }
        boolean allFound() { return total > 0 && found == total; }
    }
    static Counts counts(List<DebugSnapshot.BiomeEntry> biomes) {
        int found = 0, observed = 0, sampled = 0;
        for (var biome : biomes) {
            if (biome.found()) found++;
            if (biome.observed()) observed++;
            if (biome.sampleHits() > 0) sampled++;
        }
        return new Counts(biomes.size(), found, observed, sampled);
    }
    static List<DebugSnapshot.BiomeEntry> select(List<DebugSnapshot.BiomeEntry> biomes, Filter filter, Sort sort) {
        Comparator<DebugSnapshot.BiomeEntry> order = Comparator.comparing(DebugSnapshot.BiomeEntry::id);
        if (sort == Sort.HITS) order = Comparator.comparingInt(DebugSnapshot.BiomeEntry::sampleHits).reversed().thenComparing(order);
        return biomes.stream().filter(b -> switch (filter) {
            case ALL -> true;
            case UNCONFIRMED -> !b.found();
            case FOUND -> b.found();
        }).sorted(order).toList();
    }
    static String preset(int overworldChunks) {
        if (overworldChunks <= 0) return "Unwrapped";
        for (var preset : WorldSize.Preset.values()) if (preset.size.overworldChunks() == overworldChunks) return preset.label;
        return "Custom";
    }
    private BiomeOverview() {}
}
