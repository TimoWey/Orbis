package dev.orbis.debug;

import com.toroidalworld.core.ShapedChunkGenerator;
import com.toroidalworld.engine.gen.AddedStructureStarts;
import com.toroidalworld.mixin.StructureManagerAccessor;
import java.util.*;
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
    record Entry(String id, DebugSnapshot.Place location, boolean observed, boolean exhausted) {
        String status() { return observed ? "Observed" : location != null ? "Predicted" : exhausted ? "Not generated" : "Unconfirmed"; }
        boolean found() { return observed || location != null; }
    }
    private final ServerLevel level;
    private final int minX, minZ, width;
    private final boolean finite;
    private final SortedSet<String> expected = new TreeSet<>();
    private final Map<String, DebugSnapshot.Place> found = new HashMap<>();
    private final Set<String> observed = new HashSet<>();
    private final List<Task> tasks = new ArrayList<>();
    private boolean initialized, running;
    private int nextTask;
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
        if (expected.contains("minecraft:stronghold")) expected.add(PORTAL);
    }

    void observe(String id, DebugSnapshot.Place place) {
        expected.add(id); found.put(id, place); observed.add(id); revision++;
    }
    void running(boolean value) { running = value && finite && error.isEmpty() && !complete(); }
    boolean running() { return running; }
    long checks() { return checks; }
    long revision() { return revision; }
    String state() {
        if (!error.isEmpty()) return error;
        if (!finite) return "Visit a finite dimension to audit";
        if (!level.structureManager().shouldGenerateStructures()) return "Structure generation is disabled";
        if (allFound(entries())) return "All listed types found";
        if (complete()) return "Generator check complete";
        return running ? "Checking generator..." : checks > 0 ? "Check paused" : "Not checked yet";
    }
    static boolean allFound(List<Entry> entries) { return !entries.isEmpty() && entries.stream().allMatch(Entry::found); }
    private boolean complete() { return initialized && tasks.stream().allMatch(t -> t.done || satisfied(t)); }
    private boolean satisfied(Task task) {
        return task.ids.stream().allMatch(found::containsKey)
                && (!task.ids.contains("minecraft:stronghold") || found.containsKey(PORTAL));
    }
    List<Entry> entries() {
        return expected.stream().map(id -> {
            String target = id.equals(PORTAL) ? "minecraft:stronghold" : id;
            var relevant = tasks.stream().filter(t -> t.ids.contains(target)).toList();
            boolean exhausted = initialized && error.isEmpty() && !relevant.isEmpty() && relevant.stream().allMatch(t -> t.done);
            return new Entry(id, found.get(id), observed.contains(id), exhausted);
        }).toList();
    }

    /** At most one layout generation per tick; caller also enforces its scheduling deadline. */
    void tick() {
        if (!running) return;
        try {
            if (!level.structureManager().shouldGenerateStructures()) { running = false; return; }
            var source = level.getChunkSource();
            var state = source.getGeneratorState();
            if (!initialized) {
                var carried = ShapedChunkGenerator.carriedShapeOf(source.getGenerator());
                var added = carried == null ? AddedStructureStarts.NONE : AddedStructureStarts.of(state,
                        ((StructureManagerAccessor) level.structureManager()).toroidal$structureCheck(), carried);
                for (Task task : tasks) task.init(state, added);
                initialized = true;
                palettes = PalettedContainerFactory.create(level.registryAccess());
            }
            if (complete()) { running = false; return; }
            for (int probes = 0; probes < 32; probes++) {
                Task task = tasks.get(nextTask);
                nextTask = (nextTask + 1) % tasks.size();
                if (task.done || satisfied(task)) continue;
                ChunkPos pos = task.next(state);
                if (pos == null) { task.done = true; revision++; continue; }
                checks++;
                if (!inside(pos)) continue;
                var scratch = new ProtoChunk(pos, UpgradeData.EMPTY, level, palettes, null);
                // Use Minecraft's weighted selection, biome/height checks and Orbis added starts.
                source.getGenerator().createStructures(level.registryAccess(), state, level.structureManager(),
                        scratch, level.getStructureTemplateManager(), level.dimension());
                scratch.getAllStarts().forEach((structure, start) -> {
                    if (!start.isValid()) return;
                    String id = String.valueOf(level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(structure));
                    if (expected.contains(id)) record(id, start);
                });
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
        var p = start.getBoundingBox().getCenter();
        if (!found.containsKey(id)) { found.put(id, location(id, p.getX(), p.getY(), p.getZ())); revision++; }
        for (var piece : start.getPieces()) if (piece instanceof StrongholdPieces.PortalRoom) {
            var room = piece.getBoundingBox().getCenter();
            if (!found.containsKey(PORTAL)) { found.put(PORTAL, location(PORTAL, room.getX(), room.getY(), room.getZ())); revision++; }
        }
    }
    private DebugSnapshot.Place location(String id, int x, int y, int z) {
        return new DebugSnapshot.Place(id + " (predicted)", minX * 16 + Math.floorMod(x - minX * 16, width * 16),
                y, minZ * 16 + Math.floorMod(z - minZ * 16, width * 16));
    }

    /** Enumerates every placement region intersecting the finite world without allocating a world-sized list. */
    static final class Grid {
        final int startX, startZ, columns, spacing;
        final long total;
        Grid(int minX, int minZ, int width, int spacing) {
            this.spacing = spacing;
            startX = Math.floorDiv(minX, spacing); startZ = Math.floorDiv(minZ, spacing);
            columns = Math.floorDiv(minX + width - 1, spacing) - startX + 1;
            total = (long) columns * (Math.floorDiv(minZ + width - 1, spacing) - startZ + 1);
        }
        ChunkPos at(long index) {
            if (index < 0 || index >= total) throw new IndexOutOfBoundsException();
            return new ChunkPos((startX + (int) (index % columns)) * spacing,
                    (startZ + (int) (index / columns)) * spacing);
        }
    }

    private final class Task {
        final StructureSet set;
        final Set<String> ids;
        Iterator<ChunkPos> extra = Collections.emptyIterator();
        Iterator<ChunkPos> rings;
        Grid grid;
        long cursor;
        boolean done;
        Task(StructureSet set, Set<String> ids) { this.set = set; this.ids = ids; }
        void init(ChunkGeneratorStructureState state, AddedStructureStarts added) {
            extra = added.of(set.placement()).stream().map(a -> a.chunk()).iterator();
            if (set.placement() instanceof ConcentricRingsStructurePlacement ring) {
                var positions = state.getRingPositionsFor(ring);
                if (positions == null) throw new IllegalStateException("Ring positions unavailable");
                rings = positions.iterator();
            } else {
                int spacing = set.placement() instanceof RandomSpreadStructurePlacement spread ? spread.spacing() : 1;
                grid = new Grid(minX, minZ, width, spacing);
            }
        }
        ChunkPos next(ChunkGeneratorStructureState state) {
            if (extra.hasNext()) return extra.next();
            if (rings != null) return rings.hasNext() ? rings.next() : null;
            if (cursor >= grid.total) return null;
            ChunkPos origin = grid.at(cursor++);
            return set.placement() instanceof RandomSpreadStructurePlacement spread
                    ? spread.getPotentialStructureChunk(state.getLevelSeed(), origin.x(), origin.z()) : origin;
        }
    }
}
