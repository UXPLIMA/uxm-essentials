package com.uxplima.uxmessentials.playerstate.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerStateStore;
import com.uxplima.uxmessentials.playerstate.application.port.StateReconciler;
import com.uxplima.uxmessentials.playerstate.domain.GameModeRef;
import com.uxplima.uxmessentials.playerstate.domain.PlayerStateSnapshot;
import com.uxplima.uxmessentials.playerstate.domain.event.GameModeChanged;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /gamemode <mode> [player]} (and the {@code /gmc /gms /gma /gmsp} aliases): pin a player's game mode.
 * The snapshot is updated to carry the mode through the {@link PlayerStateStore}, the live player's mode is
 * set by the {@link StateReconciler} on the owning region thread, the {@link GameModeChanged} event is
 * published, and the actor and subject are notified. The argument is parsed to a {@link GameModeRef} in the
 * adapter; an unrecognised mode never reaches this use case.
 */
public final class SetGamemode {

    private final PlayerStateStore store;
    private final StateReconciler reconciler;
    private final PlayerStateNotifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public SetGamemode(
            PlayerStateStore store,
            StateReconciler reconciler,
            PlayerStateNotifier notifier,
            DomainEventPublisher events,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Set {@code who}'s own game mode. */
    public void set(PlayerRef who, GameModeRef mode) {
        setFor(who, who, mode);
    }

    /** Set {@code subject}'s game mode on behalf of {@code actor}. */
    public void setFor(PlayerRef actor, PlayerRef subject, GameModeRef mode) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(mode, "mode");
        PlayerStateSnapshot updated = store.update(subject, current -> current.withGameMode(mode));
        reconciler.reconcile(subject, updated);
        events.publish(new GameModeChanged(subject, actor, mode, clock.instant()));
        notify(actor, subject, mode);
    }

    private void notify(PlayerRef actor, PlayerRef subject, GameModeRef mode) {
        Map<String, String> modeName = Map.of("mode", mode.canonical());
        if (actor.equals(subject)) {
            notifier.send(actor, PlayerstateMessageKey.GAMEMODE_SET, modeName);
            return;
        }
        notifier.send(
                actor,
                PlayerstateMessageKey.GAMEMODE_SET_OTHER,
                Map.of("mode", mode.canonical(), "player", subject.name()));
        notifier.send(subject, PlayerstateMessageKey.GAMEMODE_SET, modeName);
    }
}
