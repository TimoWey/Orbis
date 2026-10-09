package dev.orbis.atlas;

import java.util.*;

/** Deduplicated start-chunk evidence. Observations and predictions must never simply be added. */
final class StructureCounts {
    static final int LIMIT = 131072;
    record Saved(Map<String, Set<Long>> observed, Map<String, Set<Long>> predicted, boolean limited) {}
    record Count(long observed, long predicted, long distinct) {}
    private final Map<String, Set<Long>> observed = new HashMap<>(), predicted = new HashMap<>();
    private final Map<String, Long> distinct = new HashMap<>();
    private final int limit;
    private int entries;
    private boolean limited;

    StructureCounts() { this(LIMIT); }
    StructureCounts(int limit) { this.limit = limit; }
    boolean add(String id, long start, boolean actual) {
        var target = actual ? observed : predicted;
        var other = actual ? predicted : observed;
        Set<Long> values = target.get(id);
        if (values != null && values.contains(start)) return false;
        if (entries >= limit) { limited = true; return false; }
        target.computeIfAbsent(id, ignored -> new HashSet<>()).add(start);
        entries++;
        if (!other.getOrDefault(id, Set.of()).contains(start)) distinct.merge(id, 1L, Long::sum);
        return true;
    }
    Count count(String id) {
        return new Count(observed.getOrDefault(id, Set.of()).size(), predicted.getOrDefault(id, Set.of()).size(), distinct.getOrDefault(id, 0L));
    }
    boolean limited() { return limited; }
    Saved save() { return new Saved(copy(observed), copy(predicted), limited); }
    void restore(Saved saved) {
        observed.clear(); predicted.clear(); distinct.clear(); entries = 0; limited = false;
        saved.observed().forEach((id, starts) -> starts.forEach(start -> add(id, start, true)));
        saved.predicted().forEach((id, starts) -> starts.forEach(start -> add(id, start, false)));
        limited |= saved.limited();
    }
    void clearPredictions() { restore(new Saved(copy(observed), Map.of(), false)); }
    private static Map<String, Set<Long>> copy(Map<String, Set<Long>> source) {
        Map<String, Set<Long>> result = new HashMap<>();
        source.forEach((id, starts) -> result.put(id, Set.copyOf(starts)));
        return Map.copyOf(result);
    }
}
