package dev.orbis.atlas;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Player atlas: local saved terrain in singleplayer; received chunks, kept per server world, on remote servers. */
public final class PlayerAtlasClient implements ClientModInitializer {
    static KeyMapping mapKey;
    static AtlasSnapshot snapshot;
    private final OrbisMinimap minimap = new OrbisMinimap();
    private final Map<String, AtlasTerrainCollector> dimensions = new HashMap<>();
    // Remote servers with Orbis publish an opaque world id; without it terrain stays in memory only.
    private final Map<String, AtlasRemoteTerrain> archives = new HashMap<>();
    private String remoteWorld;
    // Wraps the server announced per dimension; chunks arrive in the client's own continuous frame.
    private final Map<String, AtlasShape> shapes = new HashMap<>();
    private ClientLevel level;

    @Override public void onInitializeClient() {
        PlayerLocalTerrain.register();
        ClientPlayNetworking.registerGlobalReceiver(AtlasWorldPayload.TYPE, (payload, context) -> {
            if (payload.world().equals(remoteWorld)) return;
            closeArchives(); remoteWorld = payload.world();
        });
        ClientPlayNetworking.registerGlobalReceiver(AtlasShapePayload.TYPE, (payload, context) -> {
            var dimension = payload.dimension().toString();
            if (payload.shape().equals(shapes.put(dimension, payload.shape()))) return;
            // Chunks taken before the shape was known are keyed in the wrong frame; collect them again.
            dimensions.remove(dimension);
            var world = context.client().level;
            if (world != null && context.client().player != null && world.dimension().identifier().toString().equals(dimension))
                reload(world, context.client().player.chunkPosition(), context.client().options.getEffectiveRenderDistance());
        });
        mapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.orbis.atlas",
                InputConstants.Type.KEYBOARD, InputConstants.KEY_M,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("orbis", "atlas"))));
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (Minecraft.getInstance().getSingleplayerServer() == null) terrain(world).load(shape(world).foldChunk(chunk.getPos().pack()));
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            var data = dimensions.get(world.dimension().identifier().toString());
            var shape = shape(world);
            if (data != null) data.unload(shape.foldChunk(chunk.getPos().pack()), key -> shape.fold(AtlasTerrain.sample(chunk, 0xff283039)),
                    tile -> { var archive = archives.get(world.dimension().identifier().toString()); if (archive != null) archive.capture(tile); });
        });
        // Disconnect may arrive on Netty's local transport thread. Texture lifetime and
        // the collector maps belong to the client thread, just like chunk callbacks.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            dimensions.clear(); shapes.clear(); closeArchives(); remoteWorld = null;
            snapshot = null; minimap.close(); level = null; PlayerLocalTerrain.clearView();
        }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick(client);
            while (mapKey.consumeClick()) if (client.level != null && client.player != null)
                client.gui.setScreen(new PlayerAtlasScreen());
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("orbis", "minimap"), (g, delta) -> minimap.draw(g, snapshot));
    }
    private AtlasShape shape(ClientLevel world) {
        return shapes.getOrDefault(world.dimension().identifier().toString(), AtlasShape.OPEN);
    }
    private void reload(ClientLevel world, ChunkPos centre, int radius) {
        var cells = terrain(world);
        var shape = shape(world);
        for (int x = centre.x() - radius; x <= centre.x() + radius; x++)
            for (int z = centre.z() - radius; z <= centre.z() + radius; z++)
                if (world.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) != null)
                    cells.load(shape.foldChunk(ChunkPos.pack(x, z)));
    }
    private AtlasTerrainCollector terrain(ClientLevel world) {
        return dimensions.computeIfAbsent(world.dimension().identifier().toString(), key -> new AtlasTerrainCollector());
    }
    private void tick(Minecraft client) {
        if (client.level != level) {
            minimap.close(); snapshot = null; level = client.level;
            if (level == null) { dimensions.clear(); closeArchives(); remoteWorld = null; PlayerLocalTerrain.clearView(); }
        }
        if (level == null || client.player == null) return;
        var local = client.getSingleplayerServer();
        if (local != null) {
            snapshot = PlayerLocalTerrain.view(local, level.dimension(), client.player.getUUID());
            return;
        }
        var dimension = level.dimension().identifier().toString();
        var cells = terrain(level);
        var shape = shape(level);
        boolean wrapped = shape.wrapped();
        int span = shape.span(), minX = shape.minX(), minZ = shape.minZ();
        var archive = archive(dimension, cells, minX, minZ, span);
        int playerX = client.player.getBlockX() >> 4, playerZ = client.player.getBlockZ() >> 4;
        // Keys are folded; each is read from the client's image of that chunk nearest to the player.
        cells.tick(shape.foldChunkX(playerX), shape.foldChunkZ(playerZ), key -> {
            int x = shape.nearestChunk(ChunkPos.getX(key), playerX), z = shape.nearestChunk(ChunkPos.getZ(key), playerZ);
            var chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
            return chunk == null ? null : shape.fold(AtlasTerrain.sample(chunk, 0xff283039));
        }, archive == null ? tile -> {} : archive::capture);
        if (archive != null) archive.tick();
        snapshot = new AtlasSnapshot(dimension, 0, "", cells.revision(), minX, minZ, span, wrapped,
                client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ(), 0,
                // Loaded chunks select live pixels over older archived ones.
                cells.cells(), archive == null ? Set.of() : cells.loadedSnapshot(), List.of(), List.of(), Map.of(),
                archive == null && cells.cells().size() >= AtlasTerrainCollector.HISTORY_LIMIT, "",
                List.of(), false, 0, "", new AtlasSearch.View(new AtlasSearch.Saved(0, 1, false, 0, 256, false), false),
                false, new AtlasSnapshot.MapLayers(cells.revision(), 0, archive == null ? 0 : cells.loadedRevision(), 0),
                archive == null ? null : archive.archive());
    }
    private AtlasRemoteTerrain archive(String dimension, AtlasTerrainCollector cells, int minX, int minZ, int span) {
        if (remoteWorld == null) return null;
        var archive = archives.get(dimension);
        if (archive == null) {
            archive = new AtlasRemoteTerrain(AtlasRemoteTerrain.directory(
                    FabricLoader.getInstance().getGameDir().resolve("orbis-atlas-cache"), remoteWorld, dimension, minX, minZ, span));
            // Terrain captured before the world id arrived is kept too.
            cells.cells().forEach(archive::capture);
            archives.put(dimension, archive);
        }
        return archive;
    }
    private void closeArchives() { archives.values().forEach(AtlasRemoteTerrain::close); archives.clear(); }
}
