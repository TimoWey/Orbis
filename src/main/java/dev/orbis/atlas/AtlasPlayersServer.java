package dev.orbis.atlas;

import dev.orbis.Orbis;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Server-owned positions also cover players outside entity tracking range. */
public final class AtlasPlayersServer {
    // Server thread only.
    private static final Set<ServerGamePacketListenerImpl> WORLD_SENT = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<ServerGamePacketListenerImpl, AtlasShapePayload> SHAPE_SENT = new WeakHashMap<>();
    private static WeakReference<MinecraftServer> worldServer = new WeakReference<>(null);
    private static AtlasWorldPayload world;
    private static boolean worldFailed;
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(AtlasPlayersPayload.TYPE, AtlasPlayersPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AtlasWorldPayload.TYPE, AtlasWorldPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AtlasShapePayload.TYPE, AtlasShapePayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 != 0) return;
            for (var level : server.getAllLevels()) {
                var recipients = level.players().stream()
                        .filter(p -> ServerPlayNetworking.canSend(p, AtlasPlayersPayload.TYPE)).toList();
                if (recipients.isEmpty()) continue;
                var players = level.players().stream().filter(p -> !p.isSpectator() && !p.isInvisible())
                        .limit(AtlasPlayersPayload.MAX_PLAYERS)
                        .map(p -> new AtlasPlayersPayload.Player(p.getUUID(), p.getGameProfile().name(),
                                p.getX(), p.getZ(), p.getYRot())).toList();
                var payload = new AtlasPlayersPayload(level.dimension().identifier(), players);
                var shape = AtlasShapePayload.of(level.dimension().identifier(), AtlasShape.server(level));
                for (var recipient : recipients) {
                    // Once per connection: lets map clients keep explored terrain across reconnects.
                    if (ServerPlayNetworking.canSend(recipient, AtlasWorldPayload.TYPE) && WORLD_SENT.add(recipient.connection)) {
                        var id = world(server);
                        if (id != null) ServerPlayNetworking.send(recipient, id);
                    }
                    // Whenever the recipient enters a dimension, so its map folds what it receives there.
                    if (ServerPlayNetworking.canSend(recipient, AtlasShapePayload.TYPE)
                            && !shape.equals(SHAPE_SENT.put(recipient.connection, shape)))
                        ServerPlayNetworking.send(recipient, shape);
                    ServerPlayNetworking.send(recipient, payload);
                }
            }
        });
    }
    private static AtlasWorldPayload world(MinecraftServer server) {
        if (worldServer.get() != server) { worldServer = new WeakReference<>(server); world = null; worldFailed = false; }
        if (world == null && !worldFailed) try {
            world = new AtlasWorldPayload(AtlasCache.hash(AtlasWorldIdentity.prepare(server, server.overworld().getSeed())));
        } catch (IOException | RuntimeException failure) {
            worldFailed = true;
            Orbis.LOGGER.warn("Atlas world id unavailable; map clients keep terrain in memory only", failure);
        }
        return world;
    }
    private AtlasPlayersServer() {}
}
