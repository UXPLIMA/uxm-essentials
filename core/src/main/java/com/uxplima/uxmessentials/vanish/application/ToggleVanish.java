package com.uxplima.uxmessentials.vanish.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishBuffs;
import com.uxplima.uxmessentials.vanish.application.port.VanishBus;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * {@code /vanish}: flip a player's vanish state through the single {@link VanishStore} authority. Vanishing resolves
 * the player's use level from their permissions ({@link VanishLevelResolver}), marks them in the store at that level,
 * hides them from every viewer whose see level is below it through the {@link VanishView}, and applies the configured
 * buffs (night vision, flight) through {@link VanishBuffs}; unvanishing drops them from the store, reveals them again,
 * and clears the buffs. The actor is told their new state.
 *
 * <p>The store is the one vanish state in the plugin — messaging's {@code /msg} resolution, the nametag viewer cull,
 * and staff-mode vanish all read or mutate through it (or a query backed by it), so there is never a second flag to
 * keep in sync. Staff mode asks for an absolute set through {@link #setVanished}, which toggles only when the live
 * state differs from the requested one, leaving an already-correctly-vanished player untouched. Because buffs are
 * applied here, every entry point — {@code /vanish}, {@code /vanish <player>}, the presence settings panel, and
 * staff-mode vanish — grants and clears them uniformly.
 *
 * <p>Every transition is announced to the peer backends through the {@link VanishBus} so a cluster keeps one coherent
 * vanish view: vanishing publishes the new state and level, unvanishing publishes the reveal. A quit does <em>not</em>
 * publish (a server hop must not read as an unvanish), so only a genuine {@code /vanish} flip crosses the bus. With the
 * bus {@link VanishBus#disabled() disabled} the publish is a no-op and cross-server is inert.
 */
public final class ToggleVanish {

    private final VanishStore store;
    private final VanishView view;
    private final VanishLevelResolver levels;
    private final Notifier notifier;
    private final VanishBuffs buffs;
    private final VanishBus bus;

    public ToggleVanish(
            VanishStore store,
            VanishView view,
            VanishLevelResolver levels,
            Notifier notifier,
            VanishBuffs buffs,
            VanishBus bus) {
        this.store = Objects.requireNonNull(store, "store");
        this.view = Objects.requireNonNull(view, "view");
        this.levels = Objects.requireNonNull(levels, "levels");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.buffs = Objects.requireNonNull(buffs, "buffs");
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    /** Toggle {@code who}'s vanish state; returns the new vanished flag. */
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (store.isVanished(who.uuid())) {
            store.reveal(who.uuid());
            view.reveal(who);
            buffs.clear(who);
            notifier.send(who, VanishMessageKey.VANISH_OFF);
            bus.publish(VanishSync.revealed(who));
            return false;
        }
        VanishLevel level = levels.useLevel(who);
        store.vanish(who.uuid(), level);
        view.hide(who, level);
        buffs.apply(who);
        notifier.send(who, VanishMessageKey.VANISH_ON);
        bus.publish(VanishSync.vanished(who, level));
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
