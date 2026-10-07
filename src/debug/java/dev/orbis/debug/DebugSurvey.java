package dev.orbis.debug;

import com.toroidalworld.api.v1.ToroidalWorldApi;
import dev.orbis.Orbis;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;

/** All mutable world data belongs exclusively to the server thread. No chunk loading or generation. */
final class DebugSurvey {
    static final int GRID = 256;
    static final int LAYERS = 4;
    static final int TOTAL = GRID * GRID * LAYERS;
    private static final int HISTORY_LIMIT = AtlasCache.CHUNK_LIMIT;
    private static final int PLACE_LIMIT = 4096;
    private static final Map<ServerLevel, State> worlds = new IdentityHashMap<>();
    enum Action { NONE, BIOMES, RESAMPLE, MAP, STRUCTURES, COUNT, RESET_STRUCTURES }
    private record Request(ResourceKey<Level> dimension, UUID player, long command, Action action) {}
    private static volatile Request request;
    static volatile DebugSnapshot snapshot;
    private static long command;
    static volatile String cacheStatus = "Loading";

    static void open(ResourceKey<Level> dimension, UUID player) {
        Request active = request;
        if (active != null && active.dimension.equals(dimension) && active.player.equals(player)) return;
        snapshot = null;
        cacheStatus = "Loading";
        request = new Request(dimension, player, ++command, Action.NONE);
    }
    static void act(Action action) {
        Request r = request;
        if (r != null) request = new Request(r.dimension, r.player, ++command, action);
    }
    static void close() { request = null; snapshot = null; }

    static void register() {
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> {
            State s = worlds.computeIfAbsent(level, State::new);
            long key = chunk.getPos().pack();
            s.loaded.add(key);
            s.pending.add(key);
            s.mapRevision++;
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            State s = worlds.get(level);
            if (s != null) {
                s.loaded.remove(chunk.getPos().pack());
                s.pending.remove(chunk.getPos().pack());
                s.mapRevision++;
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> worlds.values().forEach(s -> s.save(true)));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
            if (!success) return;
            for (var entry : new ArrayList<>(worlds.entrySet())) {
                State old = entry.getValue(); old.save(true);
                State replacement = new State(entry.getKey());
                replacement.loaded.addAll(old.loaded); replacement.pending.addAll(old.loaded);
                // A reload must not replay an already-consumed explicit resample command.
                replacement.lastCommand = old.lastCommand;
                worlds.put(entry.getKey(), replacement);
            }
            snapshot = null;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            // Only shutdown waits for disk. Gameplay never waits for this worker.
            try { AtlasCache.IO.submit(() -> {}).get(10, java.util.concurrent.TimeUnit.SECONDS); }
            catch (Exception e) { Orbis.LOGGER.warn("Atlas final cache write still pending", e); }
            worlds.clear();
            close();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (State state : worlds.values()) {
                state.restore();
                if (++state.cacheTicks % 600 == 0) state.save(false);
            }
            Request r = request;
            if (r == null) return;
            ServerLevel level = server.getLevel(r.dimension);
            var player = server.getPlayerList().getPlayer(r.player);
            if (level == null || player == null || player.level() != level) return;
            State s = worlds.computeIfAbsent(level, State::new);
            if (!s.ready) return; // Do not race a restore with a new survey.
            if (s.cacheTicks % 200 == 0) s.pending.addAll(s.loaded);
            if (s.lastCommand != r.command) {
                s.lastCommand = r.command;
                if (s.wrapped) switch (r.action) {
                    case BIOMES -> s.search.toggle(s.allBiomesFound());
                    case RESAMPLE -> {
                        s.hits.clear();
                        s.locations.keySet().removeIf(id -> !s.observed.contains(id));
                        s.search.resample(); s.mapRevision++;
                    }
                    case MAP -> s.search.toggleMap();
                    case STRUCTURES -> {
                        if (s.audit.running()) s.audit.running(false); else s.audit.startQuick();
                    }
                    case COUNT -> s.audit.startCount();
                    case RESET_STRUCTURES -> s.audit.resample();
                    case NONE -> { }
                }
            }
            try {
                long deadline = System.nanoTime() + 3_000_000;
                for (int count = 0; count < 2 && !s.pending.isEmpty() && request == r && System.nanoTime() < deadline; count++) {
                    long key = s.pending.iterator().next();
                    s.pending.remove(key);
                    inspect(level, s, key);
                }
                // Neither a long structure count nor a full-budget biome pass may starve the other.
                boolean auditFirst = (s.ticks & 3) == 3;
                if (auditFirst && request == r && System.nanoTime() < deadline) s.audit.tick(deadline, () -> request == r);
                if (s.wrapped && (s.search.save().running() || s.search.save().mapping())) {
                    if (s.sampler == null) s.sampler = AtlasBiomeSampler.create(level.getChunkSource().getGenerator().getBiomeSource(),
                            level.getChunkSource().randomState(), s.minX, s.minZ, s.span, s.heights);
                    s.search.search(Math.min(deadline, System.nanoTime() + 1_000_000), () -> request == r,
                            s::allBiomesFound, s.sampler::query, s::evidence);
                    s.search.map(Math.min(deadline, System.nanoTime() + 1_000_000), () -> request == r,
                            s.sampler::query, s::evidence, (x, z, grid, color) -> {
                                int size = GRID / grid;
                                // Coarse biome preview only; explored block colors stay in their own layer.
                                for (int dz = 0; dz < size; dz++) for (int dx = 0; dx < size; dx++)
                                    s.colors.put((z * size + dz) * GRID + x * size + dx, color);
                                s.mapRevision++;
                            });
                }
                if (!auditFirst && request == r && System.nanoTime() < deadline) s.audit.tick(deadline, () -> request == r);
                if (s.auditRevision != s.audit.revision()) {
                    s.auditRevision = s.audit.revision();
                    s.mapRevision++;
                }
            } catch (RuntimeException failure) {
                s.search.pause(); s.search.pauseMap();
                s.pending.clear();
                if (s.error.isEmpty()) Orbis.LOGGER.warn("Orbis debug survey stopped", failure);
                s.error = "Survey stopped: " + failure.getClass().getSimpleName();
            }
            if (++s.ticks % 10 == 0 || snapshot == null) {
                Set<String> ids = new TreeSet<>(s.possible);
                // The checklist is the dimension generator's possible set, not a union of dimensions.

                List<DebugSnapshot.BiomeEntry> biomes = ids.stream().map(id ->
                        new DebugSnapshot.BiomeEntry(id, s.observed.contains(id), s.locations.get(id), s.hits.getOrDefault(id, 0))).toList();
                // Share immutable geometry until it changes; live tick statistics do not invalidate the map.
                if (s.cachedRevision != s.mapRevision) {
                    s.cachedChunks = List.copyOf(s.history.values());
                    s.cachedLoaded = Set.copyOf(s.loaded);
                    var markers = new ArrayList<>(s.places.values());
                    for (var entry : s.audit.entries()) if (entry.location() != null && !markers.contains(entry.location())) markers.add(entry.location());
                    s.cachedPlaces = List.copyOf(markers);
                    s.cachedColors = Map.copyOf(s.colors);
                    s.cachedRevision = s.mapRevision;
                }
                DebugSnapshot next = new DebugSnapshot(r.dimension.identifier().toString(), level.getSeed(), s.preset,
                        s.mapRevision, s.minX, s.minZ,
                        s.span, s.wrapped, player.getBlockX(), player.getBlockY(), player.getBlockZ(),
                        server.getAverageTickTimeNanos() / 1_000_000.0, s.cachedChunks,
                        s.cachedLoaded, biomes, s.cachedPlaces, s.cachedColors,
                        s.search.save().cursor(), s.search.save().limit(), s.search.save().running(), s.limited, s.error, s.audit.entries(),
                        s.audit.running(), s.audit.checks(), s.audit.state(), new AtlasSearch.View(s.search.save(), s.allBiomesFound()), s.audit.counting());
                if (request == r) { snapshot = next; cacheStatus = s.cacheStatus; }
            }
        });
    }

    private static void inspect(ServerLevel level, State s, long key) {
        int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
        var chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) return;
        int x = cx * 16 + 8, z = cz * 16 + 8;
        int y = level.dimensionType().hasCeiling() ? DebugTerrain.sliceY(level.getMinY(), level.getMaxY())
                : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
        String surface = id(chunk.getNoiseBiome(x >> 2, y >> 2, z >> 2));
        var tile = DebugTerrain.sample(chunk, DebugMapMath.biomeColor(surface));
        var previous = s.history.get(key);
        if (previous == null || previous.color() != tile.color() || !previous.terrain().equals(tile.terrain())) {
            putBounded(s.history, key, tile, HISTORY_LIMIT, s);
            s.mapRevision++;
        }
        // Locate cells only for newly observed palette entries, not all cells in every chunk.
        for (int section = 0; section < chunk.getSections().length; section++) {
            var palette = chunk.getSection(section);
            Set<String> unseen = new HashSet<>();
            palette.getBiomes().getAll(b -> { String name = id(b); if (!s.observed.contains(name)) unseen.add(name); });
            if (unseen.isEmpty()) continue;
            search:
            for (int by = 0; by < 4; by++) for (int bz = 0; bz < 4; bz++) for (int bx = 0; bx < 4; bx++) {
                String biome = id(palette.getNoiseBiome(bx, by, bz));
                if (unseen.remove(biome)) {
                    s.observed.add(biome);
                    s.mapRevision++;
                    s.locations.put(biome, new DebugSnapshot.Place(biome,
                            cx * 16 + bx * 4, chunk.getMinY() + section * 16 + by * 4, cz * 16 + bz * 4));
                    if (unseen.isEmpty()) break search;
                }
            }
        }
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        chunk.getAllStarts().forEach((structure, start) -> {
            if (!start.isValid()) return;
            var pos = start.getBoundingBox().getCenter();
            String label = String.valueOf(registry.getKey(structure));
            putPlace(s, label, pos.getX(), pos.getY(), pos.getZ());
            s.audit.observe(label, new DebugSnapshot.Place(label, pos.getX(), pos.getY(), pos.getZ()), start.getChunkPos());
            for (var piece : start.getPieces()) {
                if (piece instanceof StrongholdPieces.PortalRoom) {
                    var room = piece.getBoundingBox().getCenter();
                    putPlace(s, "End portal room (layout)", room.getX(), room.getY(), room.getZ());
                    s.audit.observe(StructureAudit.PORTAL, new DebugSnapshot.Place(StructureAudit.PORTAL, room.getX(), room.getY(), room.getZ()), start.getChunkPos());
                }
            }
        });
        Map<String, DebugSnapshot.Place> portals = new LinkedHashMap<>();
        chunk.getBlockEntities().forEach((pos, entity) -> {
            String label = entity instanceof TheEndGatewayBlockEntity ? "End gateway (seen)"
                    : entity instanceof TheEndPortalBlockEntity ? "End portal block (seen)" : null;
            if (label != null) {
                var p = new DebugSnapshot.Place(label, pos.getX(), pos.getY(), pos.getZ());
                portals.put(label + ":" + p.x() + ":" + p.y() + ":" + p.z(), p);
            }
        });
        boolean removed = s.places.entrySet().removeIf(e -> {
            var p = e.getValue();
            return (p.label().equals("End gateway (seen)") || p.label().equals("End portal block (seen)"))
                    && (p.x() >> 4) == cx && (p.z() >> 4) == cz && !portals.containsKey(e.getKey());
        });
        if (removed) s.mapRevision++;
        portals.values().forEach(p -> putPlace(s, p.label(), p.x(), p.y(), p.z()));

    }

    private static void putPlace(State s, String label, int x, int y, int z) {
        var place = new DebugSnapshot.Place(label, x, y, z);
        String key = label + ":" + x + ":" + y + ":" + z;
        if (!place.equals(s.places.get(key))) {
            putBounded(s.places, key, place, PLACE_LIMIT, s);
            s.mapRevision++;
        }
    }
    private static <K, V> void putBounded(LinkedHashMap<K, V> map, K key, V value, int limit, State s) {
        if (!map.containsKey(key) && map.size() >= limit) {
            map.remove(map.keySet().iterator().next());
            s.limited = true;
        }
        map.remove(key);
        map.put(key, value);
    }
    private static String id(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.identifier().toString()).orElse("unregistered");
    }
    private static final class State {
        final Set<Long> loaded = new HashSet<>();
        final Set<Long> pending = new LinkedHashSet<>();
        final LinkedHashMap<Long, DebugSnapshot.Cell> history = new LinkedHashMap<>();
        final LinkedHashMap<String, DebugSnapshot.Place> places = new LinkedHashMap<>();
        final Map<String, DebugSnapshot.Place> locations = new HashMap<>();
        final Set<String> observed = new HashSet<>(), possible = new HashSet<>();
        final Map<Integer, Integer> colors = new HashMap<>();
        final Map<String, Integer> hits = new HashMap<>();
        final int minX, minZ, span;
        final boolean wrapped;
        final String preset;
        final StructureAudit audit;
        final AtlasSearch search;
        final int[] heights;
        java.util.concurrent.CompletableFuture<AtlasCache.Loaded> loading;
        java.util.concurrent.CompletableFuture<Void> writing;
        AtlasCache.Loaded cache;
        boolean ready;
        int cacheTicks;
        volatile String cacheStatus = "Loading";
        long savedRevision = -1, savedChecks = -1;
        AtlasSearch.Saved savedSearch;
        boolean savedAudit;
        StructureSearchPolicy savedPolicy;
        void restore() {
            if (ready || !loading.isDone()) return;
            cache = loading.join(); loading = null; ready = true; cacheStatus = cache.status();
            var d = cache.data();
            cache = new AtlasCache.Loaded(cache.file(), cache.fingerprint(), null, cache.status());
            if (d == null) {
                return;
            }
            search.restore(d.search()); limited = d.limited();
            colors.putAll(d.colors()); hits.putAll(d.hits()); observed.addAll(d.observed()); locations.putAll(d.locations());
            for (var p : d.places()) places.put(p.label() + ":" + p.x() + ":" + p.y() + ":" + p.z(), p);
            for (var c : d.chunks()) history.put(ChunkPos.pack(c.x(), c.z()), c);
            audit.restore(d.audit()); mapRevision++;
        }
        void save(boolean force) {
            if (!ready || cache.file() == null || !force && writing != null && !writing.isDone()) return;
            if (!force && (writing == null || !writing.isCompletedExceptionally()) && savedRevision == mapRevision && search.save().equals(savedSearch) && savedChecks == audit.checks()
                    && savedAudit == audit.running() && audit.policy().equals(savedPolicy)) return;
            var d = new AtlasCache.Data(search.save(), limited, Map.copyOf(colors), Map.copyOf(hits),
                    Set.copyOf(observed), Map.copyOf(locations), List.copyOf(places.values()), List.copyOf(history.values()), audit.save());
            long revision = mapRevision;
            savedRevision = revision; savedSearch = search.save(); savedChecks = audit.checks(); savedAudit = audit.running(); savedPolicy = audit.policy();
            writing = java.util.concurrent.CompletableFuture.runAsync(() -> {
                try { AtlasCache.write(cache.file(), cache.fingerprint(), d); cacheStatus = "Saved / persistent"; }
                catch (java.io.IOException | RuntimeException e) {
                    cacheStatus = "Write failed; retrying";
                    Orbis.LOGGER.warn("Atlas cache write failed", e);
                    throw new java.util.concurrent.CompletionException(e);
                }
            }, AtlasCache.IO);
        }
        long auditRevision;
        List<DebugSnapshot.Cell> cachedChunks = List.of();
        Set<Long> cachedLoaded = Set.of();
        List<DebugSnapshot.Place> cachedPlaces = List.of();
        Map<Integer, Integer> cachedColors = Map.of();
        AtlasBiomeSampler sampler;
        int ticks;
        long lastCommand;
        long mapRevision, cachedRevision = -1;
        boolean limited;
        String error = "";
        boolean allBiomesFound() { return !possible.isEmpty() && locations.keySet().containsAll(possible); }
        void evidence(int x, int y, int z, String biome) {
            if (!locations.containsKey(biome)) { locations.put(biome, new DebugSnapshot.Place(biome, x, y, z)); mapRevision++; }
            hits.merge(biome, 1, Integer::sum);
        }
        State(ServerLevel level) {
            var overworld = ToroidalWorldApi.shapeOf(level.getServer().overworld()).orElse(null);
            preset = BiomeOverview.preset(overworld != null && overworld.loops(Direction.Axis.X)
                    ? overworld.widthChunks(Direction.Axis.X) : 0);
            var shape = ToroidalWorldApi.shapeOf(level).orElse(null);
            wrapped = shape != null && shape.loops(Direction.Axis.X) && shape.loops(Direction.Axis.Z);
            minX = wrapped ? shape.minBlock(Direction.Axis.X) : -2048;
            minZ = wrapped ? shape.minBlock(Direction.Axis.Z) : -2048;
            span = wrapped ? shape.widthBlocks(Direction.Axis.X) : 4096;
            heights = new int[] {level.dimensionType().hasCeiling() ? DebugTerrain.CEILING_SLICE_Y : level.getSeaLevel(),
                    level.getMinY() + 16, 32, 128};
            search = new AtlasSearch(minX, minZ, span, level.getMinY(), level.getMaxY(), heights, wrapped);
            audit = new StructureAudit(level, wrapped, minX, minZ, span);
            level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().forEach(b -> possible.add(id(b)));
            loading = AtlasCache.load(level, minX, minZ, span);
        }
    }
    private DebugSurvey() {}
}
