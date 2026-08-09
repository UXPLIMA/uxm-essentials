package com.uxplima.uxmessentials.playerstate.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.query.UxmPlayerStateQuery;
import com.uxplima.uxmessentials.api.view.UxmGameMode;
import com.uxplima.uxmessentials.api.view.UxmPlayerState;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerStateStore;
import com.uxplima.uxmessentials.playerstate.domain.GameModeRef;
import com.uxplima.uxmessentials.playerstate.domain.PlayerStateSnapshot;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import org.jspecify.annotations.NullMarked;

/**
 * The published player-state query, over the same in-memory snapshot the toggles write and the reconciler reads.
 *
 * <p>Nothing waits: the state is a map of the players who are online. The online check in front of it is what keeps
 * a question from creating an answer, since the store seeds a neutral snapshot for anybody it is asked about; a
 * player who is here has one already, and a player who is not should not be given one by being enquired about.
 */
@NullMarked
public final class PlayerStateQueries implements UxmPlayerStateQuery {

    private final PlayerStateStore store;
    private final PlayerLookup players;

    public PlayerStateQueries(PlayerStateStore store, PlayerLookup players) {
        this.store = Objects.requireNonNull(store, "store");
        this.players = Objects.requireNonNull(players, "players");
    }

    @Override
    public Optional<UxmPlayerState> of(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!players.isOnline(playerId)) {
            return Optional.empty();
        }
        PlayerStateSnapshot snapshot = store.current(ApiValues.subject(players, playerId));
        return Optional.of(new UxmPlayerState(
                playerId,
                snapshot.god(),
                snapshot.fly(),
                snapshot.gameMode().map(PlayerStateQueries::mode),
                snapshot.walkSpeed().toWalkMultiplier(),
                snapshot.flySpeed().toFlyMultiplier()));
    }

    private static UxmGameMode mode(GameModeRef mode) {
        return switch (mode) {
            case SURVIVAL -> UxmGameMode.SURVIVAL;
            case CREATIVE -> UxmGameMode.CREATIVE;
            case ADVENTURE -> UxmGameMode.ADVENTURE;
            case SPECTATOR -> UxmGameMode.SPECTATOR;
        };
    }
}
