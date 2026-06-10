package com.uxplima.uxmessentials.playerstate.application;

import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.ClearInventoryPreferences;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /clearinventoryconfirmtoggle} (alias {@code /citoggle}): flip the player's persisted
 * {@code /clearinventory} confirmation preference and report its new state. When on, a self
 * {@code /clearinventory} asks for a second confirmation before it empties the inventory; when off (the
 * default), it clears immediately as it always has. The flag lives behind the
 * {@link ClearInventoryPreferences} port.
 */
public final class ToggleClearInventoryConfirm {

    private final ClearInventoryPreferences preferences;
    private final PlayerStateNotifier notifier;

    public ToggleClearInventoryConfirm(ClearInventoryPreferences preferences, PlayerStateNotifier notifier) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Flip {@code who}'s confirmation preference and confirm the new state to them. */
    public void toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        boolean nowOn = preferences.toggleConfirm(who);
        notifier.send(who, nowOn ? PlayerstateMessageKey.CLEAR_CONFIRM_ON : PlayerstateMessageKey.CLEAR_CONFIRM_OFF);
    }
}
