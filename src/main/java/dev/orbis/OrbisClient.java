package dev.orbis;

import net.fabricmc.api.ClientModInitializer;

public final class OrbisClient implements ClientModInitializer {
    /** Taking the server's greeting is what lets this client into an Orbis world. */
    public static void acceptHello() { acceptHello(OrbisRequired.PROTOCOL); }

    /** Answers the server's greeting as an Orbis speaking {@code protocol}; tests use another one to play an old Orbis. */
    public static void acceptHello(int protocol) {
        net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking.registerGlobalReceiver(OrbisRequired.Hello.TYPE,
                (hello, context) -> {
                    if (hello.protocol() != protocol) context.responseSender().disconnect(OrbisRequired.mismatch(hello.version()));
                });
    }

    @Override public void onInitializeClient() {
        dev.orbis.atlas.AtlasPlayersClient.register();
        acceptHello();
    }
}
