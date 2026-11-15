package com.uxplima.uxmessentials.vanish.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.StoredVanish;

/**
 * Settles a joining player's vanish before anyone else sees them. Their kept and network-reported state is seeded
 * first; then a joiner who may rejoin vanished is hidden if they are not already, and otherwise a vanished joiner has
 * their level re-derived from this backend's permissions, which also re-applies the hide and the buffs and tells the
 * peers where they now are.
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

    public ApplyVanishOnJoin(
            JoinVanishReconciler reconciler,
            ToggleVanish toggleVanish,
            SetVanishLevel setVanishLevel,
            RememberVanish remember,
            VanishStore store) {
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
        this.toggleVanish = Objects.requireNonNull(toggleVanish, "toggleVanish");
        this.setVanishLevel = Objects.requireNonNull(setVanishLevel, "setVanishLevel");
        this.remember = Objects.requireNonNull(remember, "remember");
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Settle {@code joiner}'s vanish from their kept state ({@code stored}, empty when they have none or it was not read
     * in time); {@code joinVanished} says whether they may rejoin vanished. Returns whether they ended up vanished.
     */
    public boolean join(PlayerRef joiner, Optional<StoredVanish> stored, boolean joinVanished) {
        Objects.requireNonNull(joiner, "joiner");
        Objects.requireNonNull(stored, "stored");
        reconciler.reconcile(joiner.uuid(), stored);
        if (joinVanished && !store.isVanished(joiner.uuid())) {
            toggleVanish.setVanished(joiner, true);
        } else {
            setVanishLevel.reapply(joiner);
        }
        return store.isVanished(joiner.uuid());
    }

    /**
     * Apply a kept state that was read after {@code joiner} had already joined. Returns whether it hid them: it does
     * nothing when they are already vanished, when the kept state says visible, or when they changed their vanish on
     * this backend since joining, since that change is newer than anything the read returned.
     */
    public boolean lateLoad(PlayerRef joiner, Optional<StoredVanish> stored) {
        Objects.requireNonNull(joiner, "joiner");
        Objects.requireNonNull(stored, "stored");
        if (store.isVanished(joiner.uuid())
                || remember.changedHere(joiner.uuid())
                || stored.filter(StoredVanish::vanished).isEmpty()) {
            return false;
        }
        reconciler.reconcile(joiner.uuid(), stored);
        setVanishLevel.reapply(joiner);
        return true;
    }
}
