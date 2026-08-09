package com.uxplima.uxmessentials.presence.adapter.outbound.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.query.UxmPresenceQuery;
import com.uxplima.uxmessentials.api.view.UxmPresence;
import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.presence.domain.PlayerPresence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The published presence query, over the store the AFK sweep and the {@code /afk} command share.
 *
 * <p>Nothing here hops to a worker thread, because there is nothing to wait for: presence is a small map of the
 * players who are online, and a copy of it is cheaper than the scheduling would be.
 *
 * <p>Reads go through the snapshot rather than through the store's single-player accessor, which seeds a neutral
 * state for a player it has not seen. Asking about a player is a question, and a question that quietly created a
 * presence entry for every uuid a consumer named would leak state for players who are not even here.
 */
@NullMarked
public final class PresenceQueries implements UxmPresenceQuery {

    private final PresenceStore store;

    public PresenceQueries(PresenceStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public Optional<UxmPresence> of(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return store.snapshotAll().entrySet().stream()
                .filter(entry -> entry.getKey().uuid().equals(playerId))
                .findFirst()
                .map(PresenceQueries::view);
    }

    @Override
    public boolean isAfk(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return of(playerId).map(UxmPresence::afk).orElse(false);
    }

    @Override
    public List<UxmPresence> afk() {
        return store.snapshotAll().entrySet().stream()
                .filter(entry -> entry.getValue().afk())
                .map(PresenceQueries::view)
                .toList();
    }

    private static UxmPresence view(Map.Entry<PlayerRef, PlayerPresence> entry) {
        PlayerPresence presence = entry.getValue();
        return new UxmPresence(entry.getKey().uuid(), presence.afk(), presence.afkReason(), presence.lastActivity());
    }
}
