package dev.orbis.atlas;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class AtlasPlayersClient {
    private static final AtlasPlayerRoster ROSTER = new AtlasPlayerRoster();
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(AtlasPlayersPayload.TYPE, (payload, context) ->
                ROSTER.accept(payload, System.nanoTime()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ROSTER.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ROSTER::clear));
    }
    private static List<AtlasPlayersPayload.Player> others(String dimension) {
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null
                || !client.level.dimension().identifier().toString().equals(dimension)) return List.of();
        long now = System.nanoTime();
        boolean synchronizedRoster = ROSTER.current(dimension, now);
        var players = new LinkedHashMap<UUID, AtlasPlayersPayload.Player>();
        for (var p : ROSTER.others(dimension, client.player.getUUID(), now)) players.put(p.id(), p);
        // Entity positions give nearby arrows smooth movement. Older servers still get
        // nearby-player support; synchronized servers remain authoritative about visibility.
        for (var p : client.level.players()) {
            if (p == client.player || p.isSpectator() || p.isInvisible()) { players.remove(p.getUUID()); continue; }
            if (!synchronizedRoster || players.containsKey(p.getUUID())) players.put(p.getUUID(),
                    new AtlasPlayersPayload.Player(p.getUUID(), p.getGameProfile().name(), p.getX(), p.getZ(), p.getYRot()));
        }
        return List.copyOf(players.values());
    }
    static void draw(GuiGraphicsExtractor g, AtlasSnapshot data, AtlasMapRaster.View view,
            int left, int top, int mouseX, int mouseY, boolean names) {
        for (var player : others(data.dimension())) {
            var point = AtlasPlayerMarker.project(data, view, player.x(), player.z());
            if (point == null || point[0] < 0 || point[1] < 0 || point[0] >= view.width() || point[1] >= view.height()) continue;
            int x = left + (int) Math.round(point[0]), y = top + (int) Math.round(point[1]);
            AtlasPlayerMarker.draw(g, x, y, AtlasPlayerMarker.direction(view.globe(),
                    (player.x() - data.minX()) / data.span(), (player.z() - data.minZ()) / data.span(),
                    view.yaw(), view.pitch(), player.yaw()), true);
            if (names && Math.abs(mouseX - x) <= 6 && Math.abs(mouseY - y) <= 6)
                g.setTooltipForNextFrame(Component.literal(player.name()), mouseX, mouseY);
        }
    }
    private AtlasPlayersClient() {}
}
