package com.uxplima.uxmessentials.playerstate.adapter.outbound.api;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmPlayerStateActions;
import com.uxplima.uxmessentials.api.view.UxmGameMode;
import com.uxplima.uxmessentials.playerstate.application.Feed;
import com.uxplima.uxmessentials.playerstate.application.Heal;
import com.uxplima.uxmessentials.playerstate.application.SetGamemode;
import com.uxplima.uxmessentials.playerstate.application.SetSpeed;
import com.uxplima.uxmessentials.playerstate.application.ToggleFly;
import com.uxplima.uxmessentials.playerstate.application.ToggleGod;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerStateStore;
import com.uxplima.uxmessentials.playerstate.domain.GameModeRef;
import com.uxplima.uxmessentials.playerstate.domain.SpeedValue;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiActors;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The published player-state actions, over the same use cases {@code /god}, {@code /fly} and the rest run.
 *
 * <p>The module models most of these as toggles, because a command is a keystroke and a keystroke flips things. A
 * published setter cannot be: a plugin granting flight for the length of an event would otherwise take it away from
 * everybody who already had it. So the current state is read first and the toggle runs only when it has to, which
 * makes asking for a state a player is already in a success that changes nothing.
 *
 * <p>Everything lands on the player's own thread, because all of it is state on a live player. That thread is also
 * where the state map is written from everywhere else, so the read-then-toggle above cannot race anybody.
 */
@NullMarked
public final class PlayerStateActions implements UxmPlayerStateActions {

    private final ToggleGod god;
    private final ToggleFly fly;
    private final SetGamemode gameMode;
    private final SetSpeed speed;
    private final Heal heal;
    private final Feed feed;
    private final PlayerStateStore store;
    private final PlayerLookup players;
    private final Scheduler scheduler;
    private final String source;

    public PlayerStateActions(
            PlayerStateApiWrites writes,
            PlayerStateStore store,
            PlayerLookup players,
            Scheduler scheduler,
            String source) {
        Objects.requireNonNull(writes, "writes");
        this.god = writes.god();
        this.fly = writes.fly();
        this.gameMode = writes.gameMode();
        this.speed = writes.speed();
        this.heal = writes.heal();
        this.feed = writes.feed();
        this.store = Objects.requireNonNull(store, "store");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public CompletableFuture<UxmOutcome> setGodMode(UUID playerId, boolean enabled) {
        return onPlayer(playerId, subject -> {
            if (store.current(subject).god() != enabled) {
                god.toggleFor(actor(), subject);
            }
            return UxmOutcome.ok();
        });
    }

    @Override
    public CompletableFuture<UxmOutcome> setFlying(UUID playerId, boolean enabled) {
        return onPlayer(playerId, subject -> {
            if (store.current(subject).fly() != enabled) {
                fly.toggleFor(actor(), subject);
            }
            return UxmOutcome.ok();
        });
    }

    @Override
    public CompletableFuture<UxmOutcome> setGameMode(UUID playerId, UxmGameMode mode) {
        GameModeRef wanted = mode(Objects.requireNonNull(mode, "mode"));
        return onPlayer(playerId, subject -> {
            gameMode.setFor(actor(), subject, wanted);
            return UxmOutcome.ok();
        });
    }

    @Override
    public CompletableFuture<UxmOutcome> setWalkSpeed(UUID playerId, float multiplier) {
        SpeedValue value = scale(multiplier);
        return onPlayer(playerId, subject -> {
            speed.setWalk(actor(), subject, value);
            return UxmOutcome.ok();
        });
    }

    @Override
    public CompletableFuture<UxmOutcome> setFlySpeed(UUID playerId, float multiplier) {
        SpeedValue value = scale(multiplier);
        return onPlayer(playerId, subject -> {
            speed.setFly(actor(), subject, value);
            return UxmOutcome.ok();
        });
    }

    @Override
    public CompletableFuture<UxmOutcome> heal(UUID playerId) {
        return onPlayer(playerId, subject -> {
            heal.healFor(actor(), subject);
            return UxmOutcome.ok();
        });
    }

    @Override
    public CompletableFuture<UxmOutcome> feed(UUID playerId) {
        return onPlayer(playerId, subject -> {
            feed.feedFor(actor(), subject);
            return UxmOutcome.ok();
        });
    }

    /** Resolve the player, refuse when nobody is there, and run {@code write} on the thread that owns them. */
    private CompletableFuture<UxmOutcome> onPlayer(UUID playerId, Write write) {
        Objects.requireNonNull(playerId, "playerId");
        if (!players.isOnline(playerId)) {
            return CompletableFuture.completedFuture(
                    UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "this is state on a live player"));
        }
        PlayerRef subject = ApiValues.subject(players, playerId);
        Supplier<UxmOutcome> work = () -> write.apply(subject);
        return AsyncActions.onPlayer(
                scheduler,
                subject,
                work,
                UxmOutcome.failed(UxmFailure.PLAYER_OFFLINE, "the player left before the change could be applied"));
    }

    /**
     * The operator-facing scale from the Bukkit multiplier the query publishes, so what a consumer reads back is
     * what it wrote. Out-of-range input is clamped by {@link SpeedValue} rather than refused.
     */
    private static SpeedValue scale(float multiplier) {
        return SpeedValue.of(multiplier * SpeedValue.MAX_SCALE);
    }

    private static GameModeRef mode(UxmGameMode mode) {
        return switch (mode) {
            case SURVIVAL -> GameModeRef.SURVIVAL;
            case CREATIVE -> GameModeRef.CREATIVE;
            case ADVENTURE -> GameModeRef.ADVENTURE;
            case SPECTATOR -> GameModeRef.SPECTATOR;
        };
    }

    private PlayerRef actor() {
        return ApiActors.of(source);
    }

    /** What each verb does once the player is resolved and we are on their thread. */
    @FunctionalInterface
    private interface Write {

        UxmOutcome apply(PlayerRef subject);
    }
}
