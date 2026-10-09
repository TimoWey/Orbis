package dev.orbis.atlas;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasPlayersTest {
    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");
    private static final UUID SELF = new UUID(0, 1), OTHER = new UUID(0, 2);
    private static AtlasPlayersPayload.Player player(UUID id) {
        return new AtlasPlayersPayload.Player(id, "Player", 12000.25, -123.5, 270);
    }
    @Test void packetRoundTripKeepsFarAwayPositionsAndNames() {
        var original = new AtlasPlayersPayload(OVERWORLD, List.of(player(SELF), player(OTHER)));
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AtlasPlayersPayload.CODEC.encode(buf, original);
            assertEquals(original, AtlasPlayersPayload.CODEC.decode(buf));
            assertFalse(buf.isReadable());
        } finally { buf.release(); }
    }
    @Test void invalidCountsAreRejectedBeforeAllocatingOrReadingEntries() {
        for (int count : new int[]{-1, AtlasPlayersPayload.MAX_PLAYERS + 1, Integer.MAX_VALUE}) {
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buf.writeIdentifier(OVERWORLD); buf.writeVarInt(count);
                assertThrows(IllegalArgumentException.class, () -> AtlasPlayersPayload.CODEC.decode(buf));
            } finally { buf.release(); }
        }
    }
    @Test void nonFinitePositionsCannotReachMapProjection() {
        assertThrows(IllegalArgumentException.class, () -> new AtlasPlayersPayload.Player(SELF, "A", Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AtlasPlayersPayload.Player(SELF, "A", 0, Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> new AtlasPlayersPayload.Player(SELF, "A", 0, 0, Float.NaN));
    }
    @Test void replacementRemovesDeparturesAndNeverMixesDimensionsOrOwnMarker() {
        var roster = new AtlasPlayerRoster();
        roster.accept(new AtlasPlayersPayload(OVERWORLD, List.of(player(SELF), player(OTHER))), 10);
        assertEquals(List.of(player(OTHER)), roster.others("minecraft:overworld", SELF, 11));
        assertTrue(roster.others("minecraft:the_nether", SELF, 11).isEmpty());
        roster.accept(new AtlasPlayersPayload(OVERWORLD, List.of(player(SELF))), 20);
        assertTrue(roster.others("minecraft:overworld", SELF, 21).isEmpty());
        assertTrue(roster.current("minecraft:overworld", 21));
        roster.accept(new AtlasPlayersPayload(Identifier.withDefaultNamespace("the_nether"), List.of(player(OTHER))), 30);
        assertTrue(roster.others("minecraft:overworld", SELF, 31).isEmpty());
        assertEquals(1, roster.others("minecraft:the_nether", SELF, 31).size());
    }
    @Test void disconnectAndTimeoutRemoveStaleServerPositions() {
        var roster = new AtlasPlayerRoster();
        var payload = new AtlasPlayersPayload(OVERWORLD, List.of(player(OTHER)));
        roster.accept(payload, 1);
        assertEquals(1, roster.others("minecraft:overworld", SELF, 2_999_999_999L).size());
        assertTrue(roster.others("minecraft:overworld", SELF, 3_000_000_001L).isEmpty());
        roster.accept(payload, 4_000_000_000L);
        roster.clear();
        assertTrue(roster.others("minecraft:overworld", SELF, 4_000_000_001L).isEmpty());
    }
}
