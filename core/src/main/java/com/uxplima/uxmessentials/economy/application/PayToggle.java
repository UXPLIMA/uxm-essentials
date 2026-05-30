package com.uxplima.uxmessentials.economy.application;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.PayPreferences;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /paytoggle}: flip a player's persisted accept-pay flag and tell them its new state. When off, any
 * inbound {@code /pay} to that player resolves {@code TransferError.TARGET_DISABLED} before the debit leg
 * runs, so the payer is never charged for a transfer the target refuses
 * ({@code docs/11-economy-integration.md} §9.1). The flag is a queryable key-value row behind
 * {@link PayPreferences}, not a JSON blob, and it survives relog.
 */
public final class PayToggle {

    private final PayPreferences preferences;
    private final EconomyNotifier notifier;

    public PayToggle(PayPreferences preferences, EconomyNotifier notifier) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Flip {@code who}'s accept-pay flag; returns the new value (true = accepting pay). */
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        boolean accepting = preferences.toggle(who);
        notifier.send(who, accepting ? EconomyMessageKey.PAY_TOGGLE_ON : EconomyMessageKey.PAY_TOGGLE_OFF);
        return accepting;
    }
}
