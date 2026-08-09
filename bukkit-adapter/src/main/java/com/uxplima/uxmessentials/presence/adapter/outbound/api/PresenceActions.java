package com.uxplima.uxmessentials.presence.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmPresenceActions;
import com.uxplima.uxmessentials.presence.application.MarkAfk;
import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The published presence actions, over the same use case {@code /afk} runs.
 *
 * <p>The command toggles; a published setter cannot, so the current state is read first and the toggle runs only
 * when it has to. Marking a player away who already is changes nothing and announces nothing, which is what a
 * plugin polling its own idea of idleness needs.
 *
 * <p>The state is read through the online check rather than through the store's own accessor, which seeds a
 * neutral presence for anybody it is asked about. A player who is here already has one; a player who is not should
 * not be given one by a plugin naming them.
 */
@NullMarked
public final class PresenceActions implements UxmPresenceActions {

    private final MarkAfk markAfk;
    private final PresenceStore store;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public PresenceActions(MarkAfk markAfk, PresenceStore store, PlayerLookup players, Scheduler scheduler) {
        this.markAfk = Objects.requireNonNull(markAfk, "markAfk");
        this.store = Objects.requireNonNull(store, "store");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<UxmOutcome> setAfk(UUID playerId, boolean away) {
        return apply(playerId, away, Optional.empty());
    }

    @Override
    public CompletableFuture<UxmOutcome> setAfk(UUID playerId, String reason) {
        return apply(playerId, true, Optional.of(Objects.requireNonNull(reason, "reason")));
    }

    private CompletableFuture<UxmOutcome> apply(UUID playerId, boolean away, Optional<String> reason) {
        Objects.requireNonNull(playerId, "playerId");
        if (!players.isOnline(playerId)) {
            return CompletableFuture.completedFuture(
                    UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "only somebody at a keyboard can be away from it"));
        }
        PlayerRef who = ApiValues.subject(players, playerId);
        return AsyncActions.onPlayer(
                scheduler,
                who,
                () -> {
                    if (store.current(who).afk() != away) {
                        markAfk.toggle(who, reason);
                    }
                    return UxmOutcome.ok();
                },
                UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "the player left before the change could be applied"));
    }
}
