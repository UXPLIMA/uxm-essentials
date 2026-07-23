package com.uxplima.uxmessentials.security.adapter.inbound.gui;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

import com.uxplima.uxmessentials.security.adapter.VerificationSessions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import org.jspecify.annotations.NullMarked;

/**
 * The one piece of keypad behaviour the menu engine does not model: reopening the window when a still-frozen player
 * escapes it. The engine cancels every click and drag and tears the window down on close, but a frozen player must
 * never be able to slip past verification by closing it, so this listener reopens the keypad on any close where the
 * player is still join-frozen and the close was not a deliberate handoff or teardown.
 *
 * <p>A keypad close is recognised through the {@link Menus} facade ({@link Menus#menuIdOf} on the closing inventory),
 * so a vanilla container or any other engine menu is ignored and the listener never reaches for an engine-internal
 * holder. Recognising the specific closing inventory (rather than merely "this player closed something") is also what
 * keeps the reopen from looping: the transient close a reopen's {@code openInventory} fires is of the player's own
 * inventory, not a keypad, so it does not re-trigger. A close flagged through {@link PinKeypadView#suppressNextClose} /
 * {@link PinKeypadView#closeFor} (the TOTP handoff, a successful verify, a lockout kick, or a module stop) is consumed
 * and not reopened; a still-pending, un-flagged close reopens a fresh window; any other close simply drops the
 * tracking. The reopen is scheduled onto the viewer's region thread by the engine and no-ops if they have since gone
 * offline. The re-auth prompt shares this window but is deliberately not reopened here (it is optional), because it
 * never marks the join freeze pending.
 */
@NullMarked
public final class PinKeypadCloseListener implements Listener {

    private final Menus menus;
    private final PinKeypadView view;
    private final VerificationSessions sessions;

    public PinKeypadCloseListener(Menus menus, PinKeypadView view, VerificationSessions sessions) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.view = Objects.requireNonNull(view, "view");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (menus.menuIdOf(event.getInventory())
                .filter(PinKeypadView.SPEC_ID::equals)
                .isEmpty()) {
            return;
        }
        UUID viewer = event.getPlayer().getUniqueId();
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
}
