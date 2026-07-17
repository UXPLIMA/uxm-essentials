package com.uxplima.uxmessentials.security.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link InventoryHolder} that tags a join-verification keypad window, so {@link PinKeypadListener} can recognise
 * a click or a close as belonging to one of these (and never to a vanilla container) and read who is verifying. It
 * also carries the digits entered so far as transient session state — the buffer the digit buttons append to, the
 * clear button empties, and the submit button consumes.
 *
 * <p>The entry buffer is a plaintext PIN or code in flight; it is never logged and is consumed (and the field
 * emptied) the moment submit reads it, so it lives only between keystrokes on the one player's window. The holder is
 * created first and the menu built against it; {@link #attach} then stores the built inventory so
 * {@link #getInventory()} can answer it, as Bukkit's holder contract expects.
 */
@NullMarked
final class PinKeypadHolder implements InventoryHolder {

    private final PlayerRef viewer;
    private final boolean totpEnabled;
    private final StringBuilder entry = new StringBuilder();
    private @Nullable Inventory inventory;

    PinKeypadHolder(PlayerRef viewer, boolean totpEnabled) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.totpEnabled = totpEnabled;
    }

    /** The player verifying at this window. */
    PlayerRef viewer() {
        return viewer;
    }

    /** Whether this player holds a TOTP factor, so the keypad shows the "type a code" button. */
    boolean totpEnabled() {
        return totpEnabled;
    }

    /** The digits entered so far — a plaintext code in flight, never logged. */
    String entry() {
        return entry.toString();
    }

    /** How many digits have been entered — the length the masked display renders. */
    int length() {
        return entry.length();
    }

    /** Append a single digit, up to {@code max}; a keystroke past the cap is ignored rather than overflowing. */
    void append(char digit, int max) {
        if (entry.length() < max) {
            entry.append(digit);
        }
    }

    /** Empty the buffer — the clear button, and the reset after a submitted attempt. */
    void reset() {
        entry.setLength(0);
    }

    /** Store the built menu so the holder contract can answer {@link #getInventory()}. */
    void attach(Inventory built) {
        this.inventory = Objects.requireNonNull(built, "built");
    }

    @Override
    public Inventory getInventory() {
        Inventory built = inventory;
        if (built == null) {
            throw new IllegalStateException("keypad inventory not attached yet");
        }
        return built;
    }
}
