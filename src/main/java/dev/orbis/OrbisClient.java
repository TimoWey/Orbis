package dev.orbis;

import com.toroidalworld.api.v1.client.ShapeCustomizers;
import net.fabricmc.api.ClientModInitializer;

public final class OrbisClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        new com.toroidalworld.client.ToroidalWorldFabricClient().onInitializeClient();
        ShapeCustomizers.register(OrbisShape.MODULE.id(), parent ->
                new OrbisSettingsScreen(parent, OrbisShape.MODULE.settings(), OrbisShape.MODULE::settings));
    }
}
