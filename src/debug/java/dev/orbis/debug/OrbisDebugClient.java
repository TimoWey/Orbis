package dev.orbis.debug;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class OrbisDebugClient implements ClientModInitializer {
    static KeyMapping mapKey;
    @Override public void onInitializeClient() {
        DebugSurvey.register();
        mapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.orbis.debug_map",
                InputConstants.Type.KEYBOARD, InputConstants.KEY_M,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("orbis", "debug"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
