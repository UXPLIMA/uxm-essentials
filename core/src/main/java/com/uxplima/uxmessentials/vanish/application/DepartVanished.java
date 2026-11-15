package com.uxplima.uxmessentials.vanish.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishBus;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * A player left this backend. This backend stops hiding them and, when they were vanished, tells the peers so the
 * entry it reported is dropped from their network view. The durable state is left alone: the player is still vanished,
 * and the next backend they join reads it.
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
        level.ifPresent(hiddenAt -> bus.publishDeparture(who, hiddenAt));
        return level.isPresent();
    }
}
