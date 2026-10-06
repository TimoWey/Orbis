package dev.orbis.debug;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public final class OrbisDebugClient implements ClientModInitializer {
    static KeyMapping mapKey;
    private Object activeServer;
    private String activeDimension;
    private final OrbisMinimap minimap = new OrbisMinimap();
    @Override public void onInitializeClient() {
        DebugSurvey.register();
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("orbis", "minimap"), (graphics, delta) -> minimap.draw(graphics));
        mapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.orbis.debug_map",
                InputConstants.Type.KEYBOARD, InputConstants.KEY_M,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("orbis", "debug"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Object server = client.getSingleplayerServer();
            if (server == null || client.level == null || client.player == null) {
                if (activeServer != null) { DebugSurvey.close(); minimap.close(); }
                activeServer = null;
                activeDimension = null;
            } else {
                String dimension = client.level.dimension().identifier().toString();
                if (server != activeServer || !dimension.equals(activeDimension)) {
                    minimap.close();
                    activeServer = server;
                    activeDimension = dimension;
                    DebugSurvey.open(client.level.dimension(), client.player.getUUID());
                    AtlasWaypoints.SHARED.open(client, dimension);

                }
            }
            while (mapKey.consumeClick()) {
                if (client.player == null || client.gui.screen() != null) continue;
                if (client.getSingleplayerServer() == null) {
                    client.player.sendSystemMessage(Component.literal("Orbis debug map requires a local singleplayer world."));
                    continue;
                }
                client.gui.setScreen(new OrbisDebugScreen());
            }
        });
    }
}
