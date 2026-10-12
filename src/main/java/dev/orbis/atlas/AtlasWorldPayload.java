package dev.orbis.atlas;

import java.util.HexFormat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** An opaque SHA-256 of the server's save identity. It names the client's map archive; no seed or path. */
public record AtlasWorldPayload(String world) implements CustomPacketPayload {
    public static final Type<AtlasWorldPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("orbis", "atlas_world_v1"));
    public static final StreamCodec<FriendlyByteBuf, AtlasWorldPayload> CODEC = CustomPacketPayload.codec(
            AtlasWorldPayload::write, AtlasWorldPayload::read);

    public AtlasWorldPayload {
        if (world == null || world.length() != 64 || !world.chars().allMatch(HexFormat::isHexDigit))
            throw new IllegalArgumentException("Invalid atlas world id");
    }
    private void write(FriendlyByteBuf buf) { buf.writeUtf(world, 64); }
    private static AtlasWorldPayload read(FriendlyByteBuf buf) { return new AtlasWorldPayload(buf.readUtf(64)); }
    @Override public Type<AtlasWorldPayload> type() { return TYPE; }
}
