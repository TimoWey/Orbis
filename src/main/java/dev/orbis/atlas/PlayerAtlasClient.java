package dev.orbis.atlas;

import com.mojang.blaze3d.platform.InputConstants;
import com.toroidalworld.api.v1.ToroidalWorldClientApi;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Multiplayer atlas: observes received terrain only; never asks the server for chunks or its seed. */
public final class PlayerAtlasClient implements ClientModInitializer {
    static KeyMapping mapKey;
    static AtlasSnapshot snapshot;
    private final OrbisMinimap minimap = new OrbisMinimap();
    private final Map<String, LinkedHashMap<Long, AtlasSnapshot.Cell>> dimensions = new HashMap<>();
    private ClientLevel level;
    private int cursor, ticks;
    private long revision;

    @Override public void onInitializeClient() {
        mapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.orbis.atlas",
                InputConstants.Type.KEYBOARD, InputConstants.KEY_M,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("orbis", "atlas"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick(client);
            while (mapKey.consumeClick()) if (client.level != null && client.player != null)
                client.gui.setScreen(new PlayerAtlasScreen());
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("orbis", "minimap"), (g, delta) -> minimap.draw(g, snapshot));
    }
    private void tick(Minecraft client) {
        if (client.level != level) {
            minimap.close(); snapshot = null; level = client.level; cursor = 0;
            if (level == null) dimensions.clear();
        }
        if (level == null || client.player == null) return;
        String dimension = level.dimension().identifier().toString();
        var cells = dimensions.computeIfAbsent(dimension, key -> new LinkedHashMap<>());
        int radius = Math.min(16, client.options.renderDistance().get()), side = radius * 2 + 1;
        // Four loaded chunks per tick, with a 2 ms soft budget. Chunk access cannot trigger loads.
        long deadline = System.nanoTime() + 2_000_000;
        for (int n = 0; n < 4 && System.nanoTime() < deadline; n++) {
            int index = cursor++ % (side * side);
            int x = (client.player.getBlockX() >> 4) + index % side - radius;
            int z = (client.player.getBlockZ() >> 4) + index / side - radius;
            var chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
            if (chunk == null) continue;
            var sample = AtlasTerrain.sample(chunk, 0xff283039);
            long key = ChunkPos.pack(x, z);
            var old = cells.get(key);
            if (old == null || !old.sameTerrain(sample)) { cells.put(key, sample); revision++; }
            while (cells.size() > 8192) { cells.remove(cells.keySet().iterator().next()); revision++; }
        }
        if (ticks++ % 5 != 0 && snapshot != null) return;
        var shape = ToroidalWorldClientApi.shapeOf(level).orElse(null);
        boolean wrapped = shape != null && shape.loops(Direction.Axis.X) && shape.loops(Direction.Axis.Z);
        int span = wrapped ? shape.widthBlocks(Direction.Axis.X) : 4096;
        snapshot = new AtlasSnapshot(dimension, 0, "", revision,
                wrapped ? shape.minBlock(Direction.Axis.X) : -2048,
                wrapped ? shape.minBlock(Direction.Axis.Z) : -2048, span, wrapped,
                client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ(), 0,
                List.copyOf(cells.values()), Set.of(), List.of(), List.of(), Map.of(), cells.size() >= 8192, "",
                List.of(), false, 0, "", new AtlasSearch.View(new AtlasSearch.Saved(0, 1, false, 0, 256, false), false),
                false, new AtlasSnapshot.MapLayers(revision, 0, 0, 0), null);
    }
}
