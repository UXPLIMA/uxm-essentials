package com.uxplima.uxmessentials.vanish.adapter.outbound.api;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmVanishActions;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.ToggleVanish;
import org.jspecify.annotations.NullMarked;

/**
 * The published vanish action, over the same use case {@code /vanish} runs.
 *
 * <p>Absolute rather than a toggle, and already so in the use case: hiding a player who is hidden is a no-op there
 * too. The level they are hidden at is resolved from their own permission tier as they go, which is why there is
 * nothing here to set it with.
 *
 * <p>It runs on the player's own thread, because hiding somebody rewrites who can see them.
 */
@NullMarked
public final class VanishActions implements UxmVanishActions {

    private final ToggleVanish vanish;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public VanishActions(ToggleVanish vanish, PlayerLookup players, Scheduler scheduler) {
        this.vanish = Objects.requireNonNull(vanish, "vanish");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<UxmOutcome> setVanished(UUID playerId, boolean vanished) {
        Objects.requireNonNull(playerId, "playerId");
        if (!players.isOnline(playerId)) {
            return CompletableFuture.completedFuture(
                    UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "there is nobody here to hide"));
        }
        PlayerRef who = ApiValues.subject(players, playerId);
        return AsyncActions.onPlayer(
                scheduler,
                who,
                () -> {
                    vanish.setVanished(who, vanished);
                    return UxmOutcome.ok();
                },
                UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "the player left before the change could be applied"));
    }
}
