package dev.orbis.atlas;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A complete, bounded position snapshot for one dimension. No terrain or world seed. */
public record AtlasPlayersPayload(Identifier dimension, List<Player> players) implements CustomPacketPayload {
    public static final int MAX_PLAYERS = 4096;
    public static final Type<AtlasPlayersPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("orbis", "atlas_players_v1"));
    public static final StreamCodec<FriendlyByteBuf, AtlasPlayersPayload> CODEC = CustomPacketPayload.codec(
            AtlasPlayersPayload::write, AtlasPlayersPayload::read);

    public record Player(UUID id, String name, double x, double z, float yaw) {
        public Player {
            if (id == null || name == null || name.length() > 64 || !Double.isFinite(x)
                    || !Double.isFinite(z) || !Float.isFinite(yaw)) throw new IllegalArgumentException("Invalid atlas player");
        }
    }
    public AtlasPlayersPayload {
        if (dimension == null || players.size() > MAX_PLAYERS) throw new IllegalArgumentException("Invalid atlas roster");
        players = List.copyOf(players);
    }
    private void write(FriendlyByteBuf buf) {
        buf.writeIdentifier(dimension);
        buf.writeVarInt(players.size());
        for (var p : players) {
            buf.writeUUID(p.id()); buf.writeUtf(p.name(), 64);
            buf.writeDouble(p.x()); buf.writeDouble(p.z()); buf.writeFloat(p.yaw());
        }
    }
    private static AtlasPlayersPayload read(FriendlyByteBuf buf) {
        var dimension = buf.readIdentifier();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_PLAYERS) throw new IllegalArgumentException("Invalid atlas roster size");
        var players = new java.util.ArrayList<Player>(count);
        for (int i = 0; i < count; i++) players.add(new Player(buf.readUUID(), buf.readUtf(64),
                buf.readDouble(), buf.readDouble(), buf.readFloat()));
        return new AtlasPlayersPayload(dimension, players);
    }
    @Override public Type<AtlasPlayersPayload> type() { return TYPE; }
}
