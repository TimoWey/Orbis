package dev.orbis.atlas;

import java.util.List;
import java.util.UUID;

/** Client-thread state. Replacement snapshots remove departures and dimension changes. */
final class AtlasPlayerRoster {
    private AtlasPlayersPayload snapshot;
    private long received;
    void accept(AtlasPlayersPayload payload, long now) { snapshot = payload; received = now; }
    void clear() { snapshot = null; }
    boolean current(String dimension, long now) {
        return snapshot != null && snapshot.dimension().toString().equals(dimension)
                && now - received < 3_000_000_000L;
    }
    List<AtlasPlayersPayload.Player> others(String dimension, UUID self, long now) {
        if (!current(dimension, now)) return List.of();
        return snapshot.players().stream().filter(p -> !p.id().equals(self)).toList();
    }
}
