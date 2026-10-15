package dev.orbis.atlas;

import dev.orbis.Orbis;
import java.util.*;
import java.util.concurrent.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;

/** Local-save terrain only. No biome/structure surveys, commands, or generation requests. */
final class PlayerLocalTerrain {
    private record Request(MinecraftServer server, ResourceKey<Level> dimension, UUID player) {}
    private record Published(Request request, AtlasSnapshot data) {}
    private static volatile Request request;
    private static volatile Published published;
    // Owned exclusively by the integrated server thread.
    private static final Map<ServerLevel, State> worlds = new IdentityHashMap<>();

    static AtlasSnapshot view(MinecraftServer server, ResourceKey<Level> dimension, UUID player) {
        var wanted = new Request(server, dimension, player);
        if (!wanted.equals(request)) request = wanted;
        var current = published;
        return current != null && current.request.equals(wanted) ? current.data : null;
    }
    static void clearView() { request = null; published = null; }
    static void register() {
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
            if (!level.getServer().isDedicatedServer()) state(level).terrain.load(chunk.getPos().pack());
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            var s = worlds.get(level);
            if (s != null) s.terrain.unload(chunk.getPos().pack(), key -> AtlasTerrain.sample(chunk, 0xff283039), s.importer::capture);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.isDedicatedServer()) return;
            for (var s : worlds.values()) {
                s.restore();
                if (s.ready) {
                    s.importer.tick(s.level, ++s.ticks);
                    // Fallback if the disk cache is unavailable. Never overwrite captured live terrain.
                    for (int i = 0; i < 32; i++) {
                        var imported = s.importer.poll();
                        if (imported == null) break;
                        if (!s.terrain.loaded.contains(ChunkPos.pack(imported.cell().x(), imported.cell().z())))
                            s.terrain.accept(imported.cell());
                    }
                }
            }
            var r = request;
            if (r == null || r.server != server) return;
            var player = server.getPlayerList().getPlayer(r.player);
            var level = server.getLevel(r.dimension);
            if (player == null || level == null || player.level() != level) return;
            var s = state(level);
            // Capture immediately, even while the archive fingerprint is being prepared.
            s.terrain.tick(player.getBlockX() >> 4, player.getBlockZ() >> 4, key -> {
                var chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
                return chunk == null ? null : AtlasTerrain.sample(chunk, 0xff283039);
            }, s.importer::capture);
            var data = new AtlasSnapshot(r.dimension.identifier().toString(), 0, "", s.terrain.revision(),
                    s.minX, s.minZ, s.span, s.wrapped, player.getBlockX(), player.getBlockY(), player.getBlockZ(), 0,
                    s.terrain.cells(), s.terrain.loadedSnapshot(), List.of(), List.of(), Map.of(), false, s.importer.error,
                    List.of(), false, 0, "", new AtlasSearch.View(new AtlasSearch.Saved(0,1,false,0,256,false), false),
                    false, new AtlasSnapshot.MapLayers(s.terrain.revision(),0,s.terrain.loadedRevision(),0), s.archive);
            if (request == r) published = new Published(r, data);
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (!success || server.isDedicatedServer()) return;
            AtlasCache.invalidateResources(server);
            for (var entry : new ArrayList<>(worlds.entrySet())) {
                var old = entry.getValue(); old.importer.close();
                var replacement = new State(entry.getKey());
                old.terrain.loaded.forEach(replacement.terrain::load);
                worlds.put(entry.getKey(), replacement);
            }
            published = null;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (server.isDedicatedServer()) return;
            clearView(); worlds.values().forEach(s -> s.importer.close());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (server.isDedicatedServer()) return;
            try {
                CompletableFuture.allOf(worlds.values().stream().map(s -> s.importer.completion()).toArray(CompletableFuture[]::new))
                        .get(10, TimeUnit.SECONDS);
            } catch (Exception e) { Orbis.LOGGER.warn("Player atlas final writes still pending", e); }
            worlds.clear(); AtlasCache.clear(server); clearView();
        });
    }
    private static State state(ServerLevel level) { return worlds.computeIfAbsent(level, State::new); }
    private static final class State {
        final ServerLevel level;
        final AtlasTerrainCollector terrain = new AtlasTerrainCollector();
        final AtlasTerrainImport importer;
        CompletableFuture<AtlasCache.Loaded> loading;
        final int minX, minZ, span;
        final boolean wrapped;
        AtlasTileArchive archive;
        boolean ready;
        int ticks;
        State(ServerLevel level) {
            this.level = level;
            var shape = AtlasShape.server(level);
            wrapped = shape.wrapped(); minX = shape.minX(); minZ = shape.minZ(); span = shape.span();
            importer = new AtlasTerrainImport(level);
            loading = AtlasCache.load(level, minX, minZ, span);
        }
        void restore() {
            if (ready || !loading.isDone()) return;
            ready = true;
            var cache = loading.join(); loading = null;
            if (cache.file() != null) {
                archive = new AtlasTileArchive(cache.file().resolveSibling(cache.file().getFileName() + ".tiles").resolve(cache.fingerprint()));
                importer.archive(archive);
                Orbis.LOGGER.info("Player atlas persistent terrain ready for {}", level.dimension().identifier());
            }
        }
    }
    private PlayerLocalTerrain() {}
}
