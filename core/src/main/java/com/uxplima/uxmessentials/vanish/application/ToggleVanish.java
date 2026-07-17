package com.uxplima.uxmessentials.vanish.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * {@code /vanish}: flip a player's vanish state through the single {@link VanishStore} authority. Vanishing resolves
 * the player's use level from their permissions ({@link VanishLevelResolver}), marks them in the store at that level,
 * and hides them from every viewer whose see level is below it through the {@link VanishView}; unvanishing drops them
 * from the store and reveals them again. The actor is told their new state.
 *
 * <p>The store is the one vanish state in the plugin — messaging's {@code /msg} resolution, the nametag viewer cull,
 * and staff-mode vanish all read or mutate through it (or a query backed by it), so there is never a second flag to
 * keep in sync. Staff mode asks for an absolute set through {@link #setVanished}, which toggles only when the live
 * state differs from the requested one, leaving an already-correctly-vanished player untouched.
 */
public final class ToggleVanish {

    private final VanishStore store;
    private final VanishView view;
    private final VanishLevelResolver levels;
    private final VanishNotifier notifier;

    public ToggleVanish(VanishStore store, VanishView view, VanishLevelResolver levels, VanishNotifier notifier) {
        this.store = Objects.requireNonNull(store, "store");
        this.view = Objects.requireNonNull(view, "view");
        this.levels = Objects.requireNonNull(levels, "levels");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Toggle {@code who}'s vanish state; returns the new vanished flag. */
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (store.isVanished(who.uuid())) {
            store.reveal(who.uuid());
            view.reveal(who);
            notifier.send(who, VanishMessageKey.VANISH_OFF);
            return false;
        }
        VanishLevel level = levels.useLevel(who);
        store.vanish(who.uuid(), level);
        view.hide(who, level);
        notifier.send(who, VanishMessageKey.VANISH_ON);
        return true;
    }

    /** Set {@code who}'s vanish state to {@code vanished} absolutely; a no-op when already in that state. */
    public void setVanished(PlayerRef who, boolean vanished) {
        Objects.requireNonNull(who, "who");
        if (store.isVanished(who.uuid()) != vanished) {
            toggle(who);
        }
    }

    /** Whether {@code who} is currently vanished. */
    public boolean isVanished(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return store.isVanished(who.uuid());
    }
}
