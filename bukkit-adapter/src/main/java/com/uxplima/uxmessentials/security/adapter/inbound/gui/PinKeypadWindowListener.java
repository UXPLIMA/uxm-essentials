package com.uxplima.uxmessentials.security.adapter.inbound.gui;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.security.adapter.VerificationSessions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import org.jspecify.annotations.NullMarked;

/**
 * The one piece of keypad behaviour the menu engine does not model: reopening the window when a still-frozen player
 * escapes it. The engine cancels every click and drag and tears the window down on close, but a frozen player must
 * never be able to slip past verification by closing it, so this listener reopens the keypad on any close where the
 * player is still join-frozen and the close was not a deliberate handoff or teardown.
 *
 * <p>A keypad window is recognised through the {@link Menus} facade ({@link Menus#menuIdOf} on the inventory), so a
 * vanilla container or any other engine menu is ignored and the listener never reaches for an engine-internal holder.
 * A close flagged through {@link PinKeypadView#suppressNextClose} / {@link PinKeypadView#closeFor} (the TOTP handoff,
 * a successful verify, a lockout kick, or a module stop) is consumed and not reopened; a still-pending, un-flagged
 * close reopens a fresh window; any other close simply drops the tracking.
 *
 * <p>Not every keypad close is the player leaving. Showing a window over an open one makes the server close the open
 * one first, so each pad the view puts up while another is on screen (the enrolment flow's second entry, a reopen
 * racing the player) arrives as a close of a keypad. Treating that as an escape would reopen, and the reopen would
 * fire the next close, so the pad would rebuild itself faster than anybody could tap it. The view counts the opens it
 * has asked for and not yet seen land ({@link PinKeypadView#hasOpenInFlight}); a close arriving during one of those is
 * the server making room, and is left alone. {@link #onOpen} is what tells the view an open has landed, and a quit
 * drops the count so a viewer who left mid-open cannot come back with a stale one.
 */
@NullMarked
public final class PinKeypadWindowListener implements Listener {

    private final Menus menus;
    private final PinKeypadView view;
    private final VerificationSessions sessions;

    public PinKeypadWindowListener(Menus menus, PinKeypadView view, VerificationSessions sessions) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.view = Objects.requireNonNull(view, "view");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!isKeypad(event.getInventory())) {
            return;
        }
        UUID viewer = event.getPlayer().getUniqueId();
        if (view.hasOpenInFlight(viewer)) {
            // The server closing this window to make room for one the view has already asked for. Nothing escaped,
            // and the replacement is on its way, so neither the suppress flag nor the tracking is touched.
            return;
        }
        if (view.consumeSuppress(viewer)) {
            // A deliberate handoff (to the TOTP prompt) or teardown (verify success, lockout, module stop): leave it.
            view.forget(viewer);
            return;
        }
        if (sessions.isPending(viewer)) {
            // Still frozen and they escaped the window: reopen it so verification cannot be skipped.
            view.reopen(viewer);
        } else {
            // A non-pending close that was not a deliberate teardown (a re-auth escape, a quit): drop the tracking.
            view.forget(viewer);
        }
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (isKeypad(event.getInventory())) {
            view.openLanded(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        view.forget(event.getPlayer().getUniqueId());
    }

    private boolean isKeypad(org.bukkit.inventory.Inventory inventory) {
        return menus.menuIdOf(inventory).filter(PinKeypadView::isKeypadSpec).isPresent();
    }
}
