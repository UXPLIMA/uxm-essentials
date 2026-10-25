package com.uxplima.uxmessentials.bossbar.application;

import java.util.Objects;

import com.uxplima.uxmessentials.bossbar.application.port.BossBarVisibilityStore;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/** Hide or show a player's boss bars, keep the choice, and tell the player which it is now. */
public final class ToggleBossBars {

    private final BossBarVisibilityStore store;
    private final Notifier notifier;

    public ToggleBossBars(BossBarVisibilityStore store, Notifier notifier) {
        this.store = Objects.requireNonNull(store, "store");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Flip {@code who}'s bars; whether they are hidden now. */
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return set(who, !store.hidden(who));
    }

    /** Hide or show {@code who}'s bars; whether they are hidden now. */
    public boolean set(PlayerRef who, boolean hidden) {
        Objects.requireNonNull(who, "who");
        store.setHidden(who, hidden);
        notifier.send(who, hidden ? BossBarMessageKey.BOSSBAR_HIDDEN : BossBarMessageKey.BOSSBAR_SHOWN);
        return hidden;
    }
}
