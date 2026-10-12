package dev.orbis.atlas;

import com.mojang.blaze3d.platform.InputConstants;
import com.toroidalworld.api.v1.ToroidalWorldClientApi;
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
import net.minecraft.core.Direction;
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
    private ClientLevel level;

    @Override public void onInitializeClient() {
        PlayerLocalTerrain.register();
        ClientPlayNetworking.registerGlobalReceiver(AtlasWorldPayload.TYPE, (payload, context) -> {
            if (payload.world().equals(remoteWorld)) return;
            closeArchives(); remoteWorld = payload.world();
        });
        mapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.orbis.atlas",
                InputConstants.Type.KEYBOARD, InputConstants.KEY_M,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("orbis", "atlas"))));
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (Minecraft.getInstance().getSingleplayerServer() == null) terrain(world).load(chunk.getPos().pack());
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            var data = dimensions.get(world.dimension().identifier().toString());
            if (data != null) data.unload(chunk.getPos().pack(), key -> AtlasTerrain.sample(chunk, 0xff283039),
                    tile -> { var archive = archives.get(world.dimension().identifier().toString()); if (archive != null) archive.capture(tile); });
        });
        // Disconnect may arrive on Netty's local transport thread. Texture lifetime and
        // the collector maps belong to the client thread, just like chunk callbacks.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            dimensions.clear(); closeArchives(); remoteWorld = null;
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
        var shape = ToroidalWorldClientApi.shapeOf(level).orElse(null);
        boolean wrapped = shape != null && shape.loops(Direction.Axis.X) && shape.loops(Direction.Axis.Z);
        int span = wrapped ? shape.widthBlocks(Direction.Axis.X) : 4096;
        int minX = wrapped ? shape.minBlock(Direction.Axis.X) : -2048;
        int minZ = wrapped ? shape.minBlock(Direction.Axis.Z) : -2048;
        var archive = archive(dimension, cells, minX, minZ, span);
        cells.tick(client.player.getBlockX() >> 4, client.player.getBlockZ() >> 4, key -> {
            var chunk = level.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), ChunkStatus.FULL, false);
            return chunk == null ? null : AtlasTerrain.sample(chunk, 0xff283039);
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
