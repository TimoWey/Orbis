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
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;

/** All mutable world data belongs exclusively to the server thread. No chunk loading or generation. */
final class DebugSurvey {
    static final int GRID = 256;
    static final int LAYERS = 4;
    static final int TOTAL = GRID * GRID * LAYERS;
    private static final int HISTORY_LIMIT = 32768;
    private static final int PLACE_LIMIT = 4096;
    private static final Map<ServerLevel, State> worlds = new IdentityHashMap<>();
    private record Request(ResourceKey<Level> dimension, UUID player, long command, boolean scan, boolean audit,
            boolean refresh, long scanCommand, long auditCommand) {}
    private static volatile Request request;
    static volatile DebugSnapshot snapshot;
    private static long command;

    static void open(ResourceKey<Level> dimension, UUID player) {
        snapshot = null;
        command++;
        request = new Request(dimension, player, command, false, false, true, command, command);
    }
    static void scan(boolean enabled) {
        Request r = request;
        if (r != null) {
            command++;
            request = new Request(r.dimension, r.player, command, enabled, r.audit, false, command, r.auditCommand);
        }
    }
    static void audit(boolean enabled) {
        Request r = request;
        if (r != null) {
            command++;
            request = new Request(r.dimension, r.player, command, r.scan, enabled, false, r.scanCommand, command);
        }
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
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            worlds.clear();
            close();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Request r = request;
            if (r == null) return;
            ServerLevel level = server.getLevel(r.dimension);
            var player = server.getPlayerList().getPlayer(r.player);
            if (level == null || player == null || player.level() != level) return;
            State s = worlds.computeIfAbsent(level, State::new);
            if (s.lastCommand != r.command) {
                s.lastCommand = r.command;
                boolean scanChanged = s.lastScanCommand != r.scanCommand;
                if (scanChanged) s.scanning = r.scan;
                if (s.lastAuditCommand != r.auditCommand) s.audit.running(r.audit);
                s.lastScanCommand = r.scanCommand;
                s.lastAuditCommand = r.auditCommand;
                if (scanChanged && r.scan && s.cursor == TOTAL) {
                    s.cursor = 0;
                    s.colors.clear();
                    s.hits.clear();
                    s.locations.keySet().removeIf(id -> !s.observed.contains(id));
                    s.mapRevision++;
                }
                // Reinspect already-loaded chunks, including portal activation since the last opening.
                if (r.refresh) s.pending.addAll(s.loaded);
            }
            try {
                long deadline = System.nanoTime() + 3_000_000;
                for (int count = 0; count < 8 && !s.pending.isEmpty() && System.nanoTime() < deadline; count++) {
                    long key = s.pending.iterator().next();
                    s.pending.remove(key);
                    inspect(level, s, key);
                }
                if (System.nanoTime() < deadline) s.audit.tick();
                if (s.auditRevision != s.audit.revision()) {
                    s.auditRevision = s.audit.revision();
                    s.mapRevision++;
                }
                if (s.scanning && s.wrapped) {
                    if (s.resolver == null) s.resolver = level.getChunkSource().getGenerator().getBiomeSource()
                            .createUncachedResolver(level.getChunkSource().randomState());
                    for (int count = 0; count < 256 && s.cursor < TOTAL && System.nanoTime() < deadline; count++) {
                        int cell = s.cursor / LAYERS;
                        int layer = s.cursor % LAYERS;
                        int x = DebugMapMath.sampleCoordinate(s.minX, s.span, cell % GRID, GRID);
                        int z = DebugMapMath.sampleCoordinate(s.minZ, s.span, cell / GRID, GRID);
                        int y = switch (layer) {
                            case 0 -> level.getSeaLevel();
                            case 1 -> level.getMinY() + 16;
                            case 2 -> 32;
                            default -> 128;
                        };
                        y = Math.clamp(y, level.getMinY(), level.getMaxY());
                        String id = id(s.resolver.getNoiseBiome(x >> 2, y >> 2, z >> 2));
                        s.locations.putIfAbsent(id, new DebugSnapshot.Place(id, x, y, z));
                        s.hits.merge(id, 1, Integer::sum);
                        if (layer == 0) {
                            s.colors.put(cell, DebugMapMath.biomeColor(id));
                            s.mapRevision++;
                        }
                        s.cursor++;
                    }
                    if (s.cursor == TOTAL) s.scanning = false;
                }
            } catch (RuntimeException failure) {
                s.scanning = false;
                s.pending.clear();
                if (s.error.isEmpty()) Orbis.LOGGER.warn("Orbis debug survey stopped", failure);
                s.error = "Survey stopped: " + failure.getClass().getSimpleName();
            }
            if (++s.ticks % 10 == 0 || snapshot == null) {
                Set<String> ids = new TreeSet<>(s.possible);
                ids.addAll(s.locations.keySet());
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
                        s.cursor, TOTAL, s.scanning, s.limited, s.error, s.audit.entries(),
                        s.audit.running(), s.audit.checks(), s.audit.state());
                if (request == r) snapshot = next;
            }
        });
    }

    private static void inspect(ServerLevel level, State s, long key) {
        int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
        var chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) return;
        s.mapRevision++;
        int x = cx * 16 + 8, z = cz * 16 + 8;
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
        String surface = id(chunk.getNoiseBiome(x >> 2, y >> 2, z >> 2));
        putBounded(s.history, key, new DebugSnapshot.Cell(cx, cz, DebugMapMath.biomeColor(surface)), HISTORY_LIMIT, s);
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
            s.audit.observe(label, new DebugSnapshot.Place(label, pos.getX(), pos.getY(), pos.getZ()));
            for (var piece : start.getPieces()) {
                if (piece instanceof StrongholdPieces.PortalRoom) {
                    var room = piece.getBoundingBox().getCenter();
                    putPlace(s, "End portal room (layout)", room.getX(), room.getY(), room.getZ());
                    s.audit.observe(StructureAudit.PORTAL, new DebugSnapshot.Place(StructureAudit.PORTAL, room.getX(), room.getY(), room.getZ()));
                }
            }
        });
        chunk.getBlockEntities().forEach((pos, entity) -> {
            if (entity instanceof TheEndGatewayBlockEntity)
                putPlace(s, "End gateway (seen)", pos.getX(), pos.getY(), pos.getZ());
            else if (entity instanceof TheEndPortalBlockEntity)
                putPlace(s, "End portal block (seen)", pos.getX(), pos.getY(), pos.getZ());
        });
    }

    private static void putPlace(State s, String label, int x, int y, int z) {
        putBounded(s.places, label + ":" + x + ":" + y + ":" + z,
                new DebugSnapshot.Place(label, x, y, z), PLACE_LIMIT, s);
    }
    private static <K, V> void putBounded(LinkedHashMap<K, V> map, K key, V value, int limit, State s) {
        if (!map.containsKey(key) && map.size() >= limit) {
            map.remove(map.keySet().iterator().next());
            s.limited = true;
        }
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
        long auditRevision;
        List<DebugSnapshot.Cell> cachedChunks = List.of();
        Set<Long> cachedLoaded = Set.of();
        List<DebugSnapshot.Place> cachedPlaces = List.of();
        Map<Integer, Integer> cachedColors = Map.of();
        BiomeResolver resolver;
        int cursor, ticks;
        long lastCommand, lastScanCommand, lastAuditCommand;
        long mapRevision, cachedRevision = -1;
        boolean scanning, limited;
        String error = "";
        State(ServerLevel level) {
            var overworld = ToroidalWorldApi.shapeOf(level.getServer().overworld()).orElse(null);
            preset = BiomeOverview.preset(overworld != null && overworld.loops(Direction.Axis.X)
                    ? overworld.widthChunks(Direction.Axis.X) : 0);
            var shape = ToroidalWorldApi.shapeOf(level).orElse(null);
            wrapped = shape != null && shape.loops(Direction.Axis.X) && shape.loops(Direction.Axis.Z);
            minX = wrapped ? shape.minBlock(Direction.Axis.X) : -2048;
            minZ = wrapped ? shape.minBlock(Direction.Axis.Z) : -2048;
            span = wrapped ? shape.widthBlocks(Direction.Axis.X) : 4096;
            audit = new StructureAudit(level, wrapped, minX, minZ, span);
            level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().forEach(b -> possible.add(id(b)));
        }
    }
    private DebugSurvey() {}
}
