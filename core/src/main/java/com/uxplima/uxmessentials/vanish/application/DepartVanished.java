package com.uxplima.uxmessentials.vanish.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishBus;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * A player left this backend. This backend stops hiding them and, when they were vanished, tells the peers so the
 * entry it reported is dropped from their network view, and records when they left. A backend they reach within the
 * hop window reads that as a server switch and keeps them hidden; a later return to the network finds them visible,
 * unless they may persist their vanish.
 */
public final class DepartVanished {

    private final VanishStore store;
    private final VanishBus bus;
    private final RememberVanish remember;

    public DepartVanished(VanishStore store, VanishBus bus, RememberVanish remember) {
        this.store = Objects.requireNonNull(store, "store");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.remember = Objects.requireNonNull(remember, "remember");
    }

    /** {@code who} left this backend; returns whether they were vanished here. */
    public boolean depart(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Optional<VanishLevel> level = store.levelOf(who.uuid());
        store.reveal(who.uuid());
        remember.forget(who.uuid());
        level.ifPresent(hiddenAt -> {
            remember.departed(who.uuid());
            bus.publishDeparture(who, hiddenAt);
        });
        return level.isPresent();
    }
}
