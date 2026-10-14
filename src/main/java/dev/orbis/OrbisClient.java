package dev.orbis;

import com.toroidalworld.api.v1.client.ShapeCustomizers;
import net.fabricmc.api.ClientModInitializer;

public final class OrbisClient implements ClientModInitializer {
    /** Taking the server's greeting is what lets this client into an Orbis world. */
    public static void acceptHello() {
        net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking.registerGlobalReceiver(OrbisRequired.Hello.TYPE,
                (hello, context) -> {
                    if (hello.protocol() != OrbisRequired.PROTOCOL) context.responseSender().disconnect(OrbisRequired.mismatch(hello.version()));
                });
    }

    @Override public void onInitializeClient() {
        new com.toroidalworld.client.ToroidalWorldFabricClient().onInitializeClient();
        dev.orbis.atlas.AtlasPlayersClient.register();
        acceptHello();
        ShapeCustomizers.register(OrbisShape.MODULE.id(), parent ->
                new OrbisSettingsScreen(parent, OrbisShape.MODULE.settings(), OrbisShape.MODULE::settings));
    }
}
