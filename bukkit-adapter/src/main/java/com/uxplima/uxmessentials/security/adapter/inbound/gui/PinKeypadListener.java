package com.uxplima.uxmessentials.security.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import com.uxplima.uxmessentials.security.adapter.VerificationSessions;
import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadLayout.Button;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Drives the join-verification keypad window: it cancels every click inside it (the frozen player may tap the pad but
 * never move an item), routes a button click to the right action — a digit appends, clear empties, submit verifies,
 * and the "type a code" button hands off to the TOTP prompt — and, when a still-frozen player escapes the window,
 * reopens it so there is no way to slip past verification. A close that is a deliberate handoff to the TOTP prompt is
 * marked on the view and is not reopened.
 *
 * <p>The click routing reads the entered digits straight off the {@link PinKeypadHolder}; the verify/lockout/trust
 * decision lives behind {@link KeypadActions}, so this listener is pure input plumbing.
 */
@NullMarked
public final class PinKeypadListener implements Listener {

    private final PinKeypadView view;
    private final KeypadActions actions;
    private final VerificationSessions sessions;

    public PinKeypadListener(PinKeypadView view, KeypadActions actions, VerificationSessions sessions) {
        this.view = Objects.requireNonNull(view, "view");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PinKeypadHolder holder)) {
            return;
        }
        // Lock the whole window: no item may leave or enter it, whichever inventory the click landed in.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= PinKeypadLayout.SIZE) {
            return;
        }
        route(view.buttonAt(event.getRawSlot()), holder, player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PinKeypadHolder) {
            // A drag can span the keypad even when no single click lands on a button; cancel it so no item enters or
            // leaves the frozen window.
            event.setCancelled(true);
        }
    }

    private void route(Button button, PinKeypadHolder holder, Player player) {
        switch (button.kind()) {
            case DIGIT -> view.appendDigit(holder, button.digit());
            case CLEAR -> view.clearEntry(holder);
            case SUBMIT -> actions.submit(player, holder.viewer(), view.consume(holder));
            case TOTP -> actions.requestTotp(player, holder.viewer());
            case NONE -> {}
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof PinKeypadHolder holder)) {
            return;
        }
        PlayerRef viewer = holder.viewer();
        if (view.consumeSuppress(viewer.uuid())) {
            // A deliberate handoff to the TOTP prompt — do not fight it by reopening the keypad.
            return;
        }
        if (sessions.isPending(viewer.uuid())) {
            // Still frozen and they escaped the window — reopen it so verification cannot be skipped. The reopen is
            // scheduled on the viewer's region thread and no-ops if they have since gone offline (e.g. a disconnect).
            view.reopen(holder);
        }
    }
}
