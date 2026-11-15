package com.uxplima.uxmessentials.vanish.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.StoredVanish;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * Seeds a joining player's local vanish state before the join flow hides them, from the two places that can know they
 * are vanished: their durable {@link StoredVanish} and the {@link NetworkVanishStore}, where a peer that is hiding
 * them right now has reported them. The live network report wins on the level, since it is the fresher of the two;
 * the stored state covers a backend that has not heard of them, such as one that just started.
 *
 * <p>Either source saying vanished is enough. The two only disagree for the moment a write is in flight, and hiding a
 * player for that moment costs less than showing one who meant to stay hidden.
 */
public final class JoinVanishReconciler {

    private final NetworkVanishStore network;
    private final VanishStore store;

    public JoinVanishReconciler(NetworkVanishStore network, VanishStore store) {
        this.network = Objects.requireNonNull(network, "network");
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Seed {@code joiner}'s local vanish state; returns the level they were seeded at, or empty when neither source
     * reports them vanished (nothing to seed).
     */
    public Optional<VanishLevel> reconcile(UUID joiner, Optional<StoredVanish> stored) {
        Objects.requireNonNull(joiner, "joiner");
        Objects.requireNonNull(stored, "stored");
        Optional<VanishLevel> level = network.levelOf(joiner)
                .or(() -> stored.filter(StoredVanish::vanished).map(StoredVanish::levelOrDefault));
        level.ifPresent(found -> store.vanish(joiner, found));
        return level;
    }
}
