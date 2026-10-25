package com.uxplima.uxmessentials.bossbar.application;

import java.util.Objects;

import com.uxplima.uxmessentials.bossbar.application.port.BossBarVisibilityStore;
import com.uxplima.uxmessentials.bossbar.domain.event.BossBarVisibilityToggled;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Hide or show a player's boss bars, keep the choice, and tell the player which it is now. A change is published, so
 * another plugin hears when a player hides their bars; asking for the state they are in changes nothing and publishes
 * nothing.
 */
public final class ToggleBossBars {

    private final BossBarVisibilityStore store;
    private final Notifier notifier;
    private final DomainEventPublisher events;

    public ToggleBossBars(BossBarVisibilityStore store, Notifier notifier, DomainEventPublisher events) {
        this.store = Objects.requireNonNull(store, "store");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Flip {@code who}'s bars; whether they are hidden now. */
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return set(who, !store.hidden(who));
    }

    /** Hide or show {@code who}'s bars; whether they are hidden now. */
    public boolean set(PlayerRef who, boolean hidden) {
        Objects.requireNonNull(who, "who");
        boolean changed = store.hidden(who) != hidden;
        store.setHidden(who, hidden);
        if (changed) {
            events.publish(new BossBarVisibilityToggled(who, hidden));
        }
        notifier.send(who, hidden ? BossBarMessageKey.BOSSBAR_HIDDEN : BossBarMessageKey.BOSSBAR_SHOWN);
        return hidden;
    }
}
