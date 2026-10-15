package dev.orbis;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * An Orbis world can only be played with Orbis: the client draws and moves across the seams itself. While a player
 * connects, the server greets their Orbis; a client that cannot take the greeting is turned away with a message
 * saying what to install, and an Orbis client whose protocol differs leaves with both versions named.
 */
public final class OrbisRequired {
    /** Raised whenever client and server stop understanding each other's Orbis. */
    public static final int PROTOCOL = 1;

    public record Hello(int protocol, String version) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = new Type<>(Identifier.fromNamespaceAndPath("orbis", "hello"));
        public static final StreamCodec<FriendlyByteBuf, Hello> CODEC = CustomPacketPayload.codec(Hello::write, Hello::read);
        private void write(FriendlyByteBuf buf) { buf.writeVarInt(protocol); buf.writeUtf(version, 64); }
        private static Hello read(FriendlyByteBuf buf) { return new Hello(buf.readVarInt(), buf.readUtf(64)); }
        @Override public Type<Hello> type() { return TYPE; }
    }

    static void register() {
        PayloadTypeRegistry.clientboundConfiguration().register(Hello.TYPE, Hello.CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (Orbis.widthBlocks(server) == 0) return;
            if (ServerConfigurationNetworking.canSend(handler, Hello.TYPE))
                ServerConfigurationNetworking.send(handler, new Hello(PROTOCOL, version()));
            else handler.disconnect(missing());
        });
    }

    /** What a player without Orbis reads. Their game has no Orbis translations, so the English text is what shows. */
    static Component missing() {
        return Component.translatableWithFallback("multiplayer.orbis.required",
                "This world needs the Orbis mod.\nInstall Orbis %s for Fabric (Minecraft 26.3) to join.", version());
    }

    /** What an Orbis player reads when their Orbis cannot play on this server. */
    public static Component mismatch(String server) {
        return Component.translatableWithFallback("multiplayer.orbis.mismatch",
                "This server runs Orbis %s, you have Orbis %s.\nInstall the server's version to join.", server, version());
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer("orbis").orElseThrow().getMetadata().getVersion().getFriendlyString();
    }

    private OrbisRequired() {}
}
