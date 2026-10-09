package dev.orbis.atlas;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Server-owned positions also cover players outside entity tracking range. */
public final class AtlasPlayersServer {
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(AtlasPlayersPayload.TYPE, AtlasPlayersPayload.CODEC);
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
                for (var recipient : recipients) ServerPlayNetworking.send(recipient, payload);
            }
        });
    }
    private AtlasPlayersServer() {}
}
