package com.uxplima.uxmessentials.vanish.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * Re-resolves a vanished player's use level from their permissions and re-applies their visibility across every viewer.
 * Because levels are derived from permissions rather than stored, a promotion or demotion only takes effect once this
 * runs — the natural trigger is a (re)join, mirroring PremiumVanish's "players need to rejoin for layered permission
 * changes to register". A player who is not currently vanished is left untouched.
 *
 * <p>Re-applying goes through the {@link VanishView#hide} reconciliation, which both hides the player from viewers who
 * no longer clear the new level and reveals them to viewers who now do, so a level change settles in one pass without a
 * blanket reveal flash. The stored level is updated first so every {@code canSee} reader agrees with the view.
 */
public final class SetVanishLevel {

    private final VanishStore store;
    private final VanishView view;
    private final VanishLevelResolver levels;

    public SetVanishLevel(VanishStore store, VanishView view, VanishLevelResolver levels) {
        this.store = Objects.requireNonNull(store, "store");
        this.view = Objects.requireNonNull(view, "view");
        this.levels = Objects.requireNonNull(levels, "levels");
    }

    /**
     * Re-resolve {@code who}'s use level and re-apply their visibility if they are vanished; returns the applied level,
     * or empty when {@code who} is not vanished (nothing to re-apply).
     */
    public Optional<VanishLevel> reapply(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (!store.isVanished(who.uuid())) {
            return Optional.empty();
        }
        VanishLevel level = levels.useLevel(who);
        store.vanish(who.uuid(), level);
        view.hide(who, level);
        return Optional.of(level);
    }
}
