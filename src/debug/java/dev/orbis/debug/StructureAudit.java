package dev.orbis.debug;

import com.toroidalworld.core.ShapedChunkGenerator;
import com.toroidalworld.engine.gen.AddedStructureStarts;
import com.toroidalworld.mixin.StructureManagerAccessor;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.*;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;

/** Server-thread layout audit. Scratch chunks are never installed, populated or saved. */
final class StructureAudit {
    static final String PORTAL = "orbis:end_portal_room";
    record Entry(String id, DebugSnapshot.Place location, boolean observed, boolean exhausted,
                 long observedCount, long predictedCount, long distinctCount, boolean countLimited) {
        Entry(String id, DebugSnapshot.Place location, boolean observed, boolean exhausted) {
            this(id, location, observed, exhausted, observed ? 1 : 0, !observed && location != null ? 1 : 0,
                    observed || location != null ? 1 : 0, false);
        }
        String status() { return observed ? "Observed" : location != null ? "Predicted" : exhausted ? "Not found in check" : "Unconfirmed"; }
        boolean found() { return observed || location != null; }
        // Even if the occurrence ledger is full, a retained example proves at least one.
        String countLabel() { return Math.max(distinctCount, found() ? 1 : 0) + (exhausted && !countLimited ? "" : "+"); }
        String countDetails() {
            return "Observed starts: " + observedCount + "\nPredicted starts: " + predictedCount
                    + "\nDistinct start chunks: " + Math.max(distinctCount, found() ? 1 : 0) + " (overlapping evidence counted once).\n"
                    + (countLimited ? "Count storage limit reached; lower bound only."
                    : exhausted ? "Generator candidate count complete. Saved terrain may differ."
                    : "At least this many so far; the search is incomplete. This is not a world total.");
        }
    }
    private final ServerLevel level;
    private final int minX, minZ, width;
    private final boolean finite;
    private final SortedSet<String> expected = new TreeSet<>();
    private final Map<String, DebugSnapshot.Place> found = new HashMap<>();
    private final Set<String> observed = new HashSet<>();
    private final List<Task> tasks = new ArrayList<>();
    private final StructureCounts counts = new StructureCounts();
    private final RecentLayouts recentLayouts = new RecentLayouts(4096);
    private boolean initialized, running;
    private StructureSearchPolicy policy = StructureSearchPolicy.quick(0);
    private int nextTask, nextInitialization;
    private AddedStructureStarts added;
    private PalettedContainerFactory palettes;
    private long checks, revision;
    private String error = "";

    StructureAudit(ServerLevel level, boolean finite, int minX, int minZ, int span) {
        this.level = level; this.finite = finite;
        this.minX = minX >> 4; this.minZ = minZ >> 4; this.width = span >> 4;
        var biomes = level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes();
        for (var holder : level.getChunkSource().getGeneratorState().possibleStructureSets()) {
            var set = holder.value();
            Set<String> ids = new HashSet<>();
            for (var entry : set.structures()) {
                if (entry.structure().value().biomes().stream().anyMatch(biomes::contains)) {
                    String id = entry.structure().unwrapKey().orElseThrow().identifier().toString();
                    expected.add(id); ids.add(id);
                }
            }
            if (!ids.isEmpty()) tasks.add(new Task(set, ids));
        }
        tasks.sort(Comparator.comparing(t -> new TreeSet<>(t.ids).toString()));
        if (expected.contains("minecraft:stronghold")) expected.add(PORTAL);
    }

    void observe(String id, DebugSnapshot.Place place, ChunkPos start) {
        expected.add(id);
        if (counts.add(id, startKey(start), true)) revision++;
        // Keep a stable observed example instead of alternating between loaded starts every refresh.
        if (observed.add(id)) { found.put(id, place); revision++; }
    }
    void running(boolean value) { running = value && finite && error.isEmpty() && !complete() && !counts.limited(); }
    boolean running() { return running; }
    boolean counting() { return policy.counting(); }
    StructureSearchPolicy policy() { return policy; }
    void startQuick() {
        if (policy.counting() || checks >= policy.limit()) policy = StructureSearchPolicy.quick(checks);
        running(true);
    }
    void startCount() {
        if (finished()) resample();
        policy = new StructureSearchPolicy(true, 0); running(true);
    }
    private boolean allExpectedFound() { return !expected.isEmpty() && found.keySet().containsAll(expected); }
    private boolean eligible(Task task) { return policy.needs(task.ids, found.keySet()); }
    private boolean quickComplete() {
        return !policy.counting() && tasks.stream().noneMatch(t -> !t.done && eligible(t));
    }
    boolean finished() { return complete() || counts.limited() || !error.isEmpty(); }
    long checks() { return checks; }
    long revision() { return revision; }
    String state() {
        if (!error.isEmpty()) return error;
        if (!finite) return "Visit a finite dimension to audit";
        if (!level.structureManager().shouldGenerateStructures()) return "Structure generation is disabled";
        if (counts.limited()) return "Count limit reached (lower bounds)";
        if (complete()) return "Generator count complete";
        if (!policy.counting() && allExpectedFound()) return "All types found / counts partial";
        if (quickComplete()) return "Existence check complete / partial counts";
        if (!policy.counting() && checks >= policy.limit()) return "Pass complete / counts partial";
        return running ? policy.counting() ? "Counting all structures..." : "Checking existence..."
                : checks > 0 ? "Check paused / counts partial" : "Not checked yet";
    }
    static boolean allFound(List<Entry> entries) { return !entries.isEmpty() && entries.stream().allMatch(Entry::found); }
    private boolean complete() { return tasks.stream().allMatch(t -> t.done); }
    List<Entry> entries() {
        return expected.stream().map(id -> {
            String target = id.equals(PORTAL) ? "minecraft:stronghold" : id;
            var relevant = tasks.stream().filter(t -> t.ids.contains(target)).toList();
            boolean exhausted = error.isEmpty() && !relevant.isEmpty() && relevant.stream().allMatch(t -> t.done);
            var count = counts.count(id);
            return new Entry(id, found.get(id), observed.contains(id), exhausted,
                    count.observed(), count.predicted(), count.distinct(), counts.limited());
        }).toList();
    }

    record Progress(long cursor, int extraCursor, int ringCursor, boolean done) {}
    record Saved(Map<String, DebugSnapshot.Place> found, Set<String> observed,
                 List<Progress> tasks, long checks, int nextTask, boolean running, StructureCounts.Saved counts, StructureSearchPolicy policy) {}
    Saved save() {
        return new Saved(Map.copyOf(found), Set.copyOf(observed), tasks.stream()
                .map(t -> new Progress(t.cursor, t.extraCursor, t.ringCursor, t.done)).toList(), checks, nextTask, running, counts.save(), policy);
    }
    void restore(Saved saved) {
        if (saved.tasks().size() != tasks.size()) return;
        found.putAll(saved.found()); observed.addAll(saved.observed()); counts.restore(saved.counts());
        expected.addAll(observed);
        checks = saved.checks(); nextTask = Math.floorMod(saved.nextTask(), Math.max(1, tasks.size()));
        for (int i = 0; i < tasks.size(); i++) {
            var p = saved.tasks().get(i); var t = tasks.get(i);
            t.cursor = p.cursor(); t.extraCursor = p.extraCursor(); t.ringCursor = p.ringCursor(); t.done = p.done();
        }
        policy = saved.policy();
        running = saved.running() && finite && !complete() && policy.allows(checks, allExpectedFound());
        revision++;
    }
    void resample() {
        found.keySet().removeIf(id -> !observed.contains(id));
        counts.clearPredictions();
        recentLayouts.clear();
        for (var t : tasks) { t.cursor = 0; t.extraCursor = 0; t.ringCursor = 0; t.done = false; }
        checks = 0; nextTask = 0; error = ""; policy = StructureSearchPolicy.quick(0); running = finite; revision++;
    }

    /** At most one layout per tick. The deadline prevents starting work, not overruns inside a native query. */
    void tick(long deadline, BooleanSupplier active) {
        if (!running || !active.getAsBoolean() || System.nanoTime() >= deadline) return;
        try {
            if (!level.structureManager().shouldGenerateStructures()) { running = false; return; }
            var source = level.getChunkSource();
            var state = source.getGeneratorState();
            if (complete() || quickComplete() || counts.limited() || !policy.allows(checks, allExpectedFound())) { running = false; return; }
            if (!initialized) {
                if (added == null) {
                    var carried = ShapedChunkGenerator.carriedShapeOf(source.getGenerator());
                    added = carried == null ? AddedStructureStarts.NONE : AddedStructureStarts.of(state,
                            ((StructureManagerAccessor) level.structureManager()).toroidal$structureCheck(), carried);
                }
                // Ring preparation may be expensive. Spread initialization across ticks as well
                // as candidate checks, and leave the remaining tasks untouched when paused.
                if (nextInitialization < tasks.size()) {
                    tasks.get(nextInitialization++).init(state, added);
                    return;
                }
                initialized = true;
                palettes = PalettedContainerFactory.create(level.registryAccess());
            }
            if (complete() || quickComplete() || counts.limited() || !policy.allows(checks, allExpectedFound())) { running = false; return; }
            for (int probes = 0; probes < 32 && active.getAsBoolean() && System.nanoTime() < deadline; probes++) {
                if (!policy.allows(checks, allExpectedFound())) { running = false; return; }
                Task task = tasks.get(nextTask);
                nextTask = (nextTask + 1) % tasks.size();
                if (task.done || !eligible(task)) continue;
                ChunkPos pos = task.next(state);
                if (pos == null) { task.done = true; revision++; continue; }
                if (!inside(pos) || recentLayouts.contains(pos.pack())) continue;
                checks++;
                var scratch = new ProtoChunk(pos, UpgradeData.EMPTY, level, palettes, null);
                // Use Minecraft's weighted selection, biome/height checks and Orbis added starts.
                source.getGenerator().createStructures(level.registryAccess(), state, level.structureManager(),
                        scratch, level.getStructureTemplateManager(), level.dimension());
                scratch.getAllStarts().forEach((structure, start) -> {
                    if (!start.isValid()) return;
                    String id = String.valueOf(level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(structure));
                    if (expected.contains(id)) record(id, start);
                });
                // createStructures checks every set, so another task at this chunk adds no evidence.
                // This bounded optimization need not be persisted: rechecking is always safe.
                recentLayouts.remember(pos.pack());
                return;
            }
        } catch (RuntimeException failure) {
            running = false;
            error = "Check stopped: " + failure.getClass().getSimpleName();
            dev.orbis.Orbis.LOGGER.warn("Orbis structure audit stopped", failure);
        }
    }
    private boolean inside(ChunkPos p) { return p.x() >= minX && p.x() < minX + width && p.z() >= minZ && p.z() < minZ + width; }
    private void record(String id, StructureStart start) {
        if (counts.add(id, startKey(start.getChunkPos()), false)) revision++;
        var p = start.getBoundingBox().getCenter();
        if (!found.containsKey(id)) { found.put(id, location(id, p.getX(), p.getY(), p.getZ())); revision++; }
        for (var piece : start.getPieces()) if (piece instanceof StrongholdPieces.PortalRoom) {
            if (counts.add(PORTAL, startKey(start.getChunkPos()), false)) revision++;
            var room = piece.getBoundingBox().getCenter();
            if (!found.containsKey(PORTAL)) { found.put(PORTAL, location(PORTAL, room.getX(), room.getY(), room.getZ())); revision++; }
        }
    }
    private long startKey(ChunkPos start) {
        return finite ? ChunkPos.pack(minX + Math.floorMod(start.x() - minX, width), minZ + Math.floorMod(start.z() - minZ, width)) : start.pack();
    }
    private DebugSnapshot.Place location(String id, int x, int y, int z) {
        return new DebugSnapshot.Place(id + " (predicted)", minX * 16 + Math.floorMod(x - minX * 16, width * 16),
                y, minZ * 16 + Math.floorMod(z - minZ * 16, width * 16));
    }

    /** Small rolling deduplication window; eviction permits rechecking without losing evidence. */
    static final class RecentLayouts {
        private final int capacity;
        private final LinkedHashSet<Long> chunks = new LinkedHashSet<>();
        RecentLayouts(int capacity) {
            if (capacity < 1) throw new IllegalArgumentException("Expected a positive capacity");
            this.capacity = capacity;
        }
        boolean contains(long chunk) { return chunks.contains(chunk); }
        void remember(long chunk) {
            if (chunks.add(chunk) && chunks.size() > capacity) chunks.removeFirst();
        }
        void clear() { chunks.clear(); }
    }

    /** A deterministic spatial permutation of every region; the same cursor works for Quick and Count. */
    static final class Grid {
        final int startX, startZ, columns, rows, spacing;
        final long total;
        Grid(int minX, int minZ, int width, int spacing) {
            this.spacing = spacing;
            startX = Math.floorDiv(minX, spacing); startZ = Math.floorDiv(minZ, spacing);
            columns = Math.floorDiv(minX + width - 1, spacing) - startX + 1;
            rows = Math.floorDiv(minZ + width - 1, spacing) - startZ + 1;
            total = (long) columns * rows;
        }
        ChunkPos at(long index) {
            if (index < 0 || index >= total) throw new IndexOutOfBoundsException();
            int x = 0, z = 0, width = columns, height = rows;
            // Split the longest axis and interleave the two child traversals. Unequal halves
            // append their remaining elements; every input index still has exactly one leaf.
            // Early probes visit distant quadrants instead of spending a whole pass on an edge.
            while (width > 1 || height > 1) {
                boolean splitX = width >= height;
                int half = (splitX ? width : height) / 2;
                long smaller = (long) half * (splitX ? height : width);
                boolean upper = index >= 2 * smaller || (index & 1) != 0;
                index = index < 2 * smaller ? index / 2 : index - smaller;
                if (splitX) {
                    if (upper) { x += half; width -= half; } else width = half;
                } else {
                    if (upper) { z += half; height -= half; } else height = half;
                }
            }
            return new ChunkPos((startX + x) * spacing, (startZ + z) * spacing);
        }
    }

    private final class Task {
        final StructureSet set;
        final Set<String> ids;
        List<ChunkPos> extra = List.of();
        int extraCursor, ringCursor;
        List<ChunkPos> rings;
        Grid grid;
        long cursor;
        boolean done;
        Task(StructureSet set, Set<String> ids) { this.set = set; this.ids = ids; }
        void init(ChunkGeneratorStructureState state, AddedStructureStarts added) {
            extra = added.of(set.placement()).stream().map(a -> a.chunk()).toList();
            if (set.placement() instanceof ConcentricRingsStructurePlacement ring) {
                var positions = state.getRingPositionsFor(ring);
                if (positions == null) throw new IllegalStateException("Ring positions unavailable");
                rings = List.copyOf(positions);
            } else {
                int spacing = set.placement() instanceof RandomSpreadStructurePlacement spread ? spread.spacing() : 1;
                grid = new Grid(minX, minZ, width, spacing);
            }
        }
        ChunkPos next(ChunkGeneratorStructureState state) {
            if (extraCursor < extra.size()) return extra.get(extraCursor++);
            if (rings != null) return ringCursor < rings.size() ? rings.get(ringCursor++) : null;
            if (cursor >= grid.total) return null;
            ChunkPos origin = grid.at(cursor++);
            return set.placement() instanceof RandomSpreadStructurePlacement spread
                    ? spread.getPotentialStructureChunk(state.getLevelSeed(), origin.x(), origin.z()) : origin;
        }
    }
}
