package com.uxplima.uxmessentials.vanish.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.StoredVanish;

/**
 * Settles a joining player's vanish before anyone else sees them.
 *
 * <p>A vanish ends when the player leaves the network, as it always has. The kept state only carries over when
 * {@link StoredVanish#restores} says so: the player may persist their vanish, or they left another backend within the
 * hop window, which is a server switch through the proxy. A peer that is hiding them right now, as the network view
 * reports, counts too. A kept vanish that does not carry over is cleared, so it cannot come back on a later join.
 *
 * <p>Once seeded, a joiner who may rejoin vanished is hidden if they are not already, and otherwise a vanished joiner
 * has their level re-derived from this backend's permissions, which also re-applies the hide and the buffs and tells
 * the peers where they now are.
 *
 * <p>The kept state is normally read during login. When that read did not finish in time the join goes ahead without
 * it and {@link #lateLoad} applies it once it arrives, unless the player already changed their vanish here.
 */
public final class ApplyVanishOnJoin {

    private final JoinVanishReconciler reconciler;
    private final ToggleVanish toggleVanish;
    private final SetVanishLevel setVanishLevel;
    private final RememberVanish remember;
    private final VanishStore store;
    private final Clock clock;
    private final Duration hopWindow;

    public ApplyVanishOnJoin(
            JoinVanishReconciler reconciler,
            ToggleVanish toggleVanish,
            SetVanishLevel setVanishLevel,
            RememberVanish remember,
            VanishStore store,
            Clock clock,
            Duration hopWindow) {
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
        this.toggleVanish = Objects.requireNonNull(toggleVanish, "toggleVanish");
        this.setVanishLevel = Objects.requireNonNull(setVanishLevel, "setVanishLevel");
        this.remember = Objects.requireNonNull(remember, "remember");
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.hopWindow = Objects.requireNonNull(hopWindow, "hopWindow");
    }

    /**
     * Settle {@code joiner}'s vanish from their kept state ({@code stored}, empty when they have none or it was not read
     * in time). {@code persist} says whether they may keep their vanish across leaving the network, and
     * {@code joinVanished} whether they rejoin vanished every time. Returns whether they ended up vanished.
     */
    public boolean join(PlayerRef joiner, Optional<StoredVanish> stored, boolean persist, boolean joinVanished) {
        Objects.requireNonNull(joiner, "joiner");
        Objects.requireNonNull(stored, "stored");
        reconciler.reconcile(joiner.uuid(), carried(stored, persist));
        if (joinVanished && !store.isVanished(joiner.uuid())) {
            toggleVanish.setVanished(joiner, true);
        } else {
            setVanishLevel.reapply(joiner);
        }
        boolean vanished = store.isVanished(joiner.uuid());
        clearLeftover(joiner, stored, vanished);
        return vanished;
    }

    /**
     * Apply a kept state that was read after {@code joiner} had already joined. Returns whether it hid them: it does
     * nothing when they are already vanished, when they changed their vanish on this backend since joining (that change
     * is newer than anything the read returned), or when the kept state does not carry over to this join.
     */
    public boolean lateLoad(PlayerRef joiner, Optional<StoredVanish> stored, boolean persist) {
        Objects.requireNonNull(joiner, "joiner");
        Objects.requireNonNull(stored, "stored");
        if (store.isVanished(joiner.uuid()) || remember.changedHere(joiner.uuid())) {
            return false;
        }
        Optional<StoredVanish> carried = carried(stored, persist);
        if (carried.isEmpty()) {
            clearLeftover(joiner, stored, false);
            return false;
        }
        reconciler.reconcile(joiner.uuid(), carried);
        setVanishLevel.reapply(joiner);
        return true;
    }

    private Optional<StoredVanish> carried(Optional<StoredVanish> stored, boolean persist) {
        return stored.filter(state -> state.restores(persist, clock.instant(), hopWindow));
    }

    private void clearLeftover(PlayerRef joiner, Optional<StoredVanish> stored, boolean vanished) {
        if (!vanished && stored.filter(StoredVanish::vanished).isPresent()) {
            remember.clearStored(joiner.uuid());
        }
    }
}
