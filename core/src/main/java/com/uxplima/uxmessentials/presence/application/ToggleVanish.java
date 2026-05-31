package com.uxplima.uxmessentials.presence.application;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.presence.application.port.VisibilityApplier;
import com.uxplima.uxmessentials.presence.domain.PlayerPresence;
import com.uxplima.uxmessentials.presence.domain.event.VanishToggled;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /vanish}: flip a staff member's vanish state (docs/10-feature-modules.md §15.8). The aggregate's
 * vanished flag is flipped atomically through the {@link PresenceStore}, the {@link VisibilityApplier} hides or
 * reveals the player across every other player's view (the {@code canSee} graph the messaging and teleport
 * visibility seams read), the {@link VanishToggled} event is published so the join/quit-suppression listener
 * and other plugins react, and the actor is told their new state.
 *
 * <p>Vanish is orthogonal to AFK — flipping it leaves the AFK flag and the last-activity stamp untouched — so
 * a staff member can be vanished and AFK simultaneously without one clobbering the other.
 */
public final class ToggleVanish {

    private final PresenceStore store;
    private final VisibilityApplier visibility;
    private final PresenceNotifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public ToggleVanish(
            PresenceStore store,
            VisibilityApplier visibility,
            PresenceNotifier notifier,
            DomainEventPublisher events,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Toggle {@code who}'s vanish state; returns the new vanished flag. */
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        PlayerPresence updated =
                store.update(who, presence -> presence.vanished() ? presence.unvanish() : presence.vanish());
        boolean vanished = updated.vanished();
        if (vanished) {
            visibility.hide(who);
        } else {
            visibility.reveal(who);
        }
        events.publish(new VanishToggled(who, vanished, clock.instant()));
        notifier.send(who, vanished ? PresenceMessageKey.VANISH_ON : PresenceMessageKey.VANISH_OFF);
        return vanished;
    }
}
