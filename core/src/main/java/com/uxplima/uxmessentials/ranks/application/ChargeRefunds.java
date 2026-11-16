package com.uxplima.uxmessentials.ranks.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.ranks.application.port.OwedCredits;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Gives back a charge whose rankup or prestige could not be saved. The refund is tried on the spot; when the economy
 * refuses or fails it, the amount is queued as owed in the same currency, so the player gets it exactly once.
 */
final class ChargeRefunds {

    private final Optional<RankEconomy> economy;
    private final Optional<OwedCredits> owed;
    private final Logger log;

    ChargeRefunds(Optional<RankEconomy> economy, Optional<OwedCredits> owed, Logger log) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.owed = Objects.requireNonNull(owed, "owed");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Give {@code amount} of {@code currencyId} back to {@code who}; {@code what} names the move for the log. */
    void giveBack(PlayerRef who, BigDecimal amount, String currencyId, String what) {
        try {
            if (economy.orElseThrow().deposit(who, amount, currencyId)) {
                return;
            }
            log.warn(
                    "event=" + what + "_refund_refused player={} amount={} currency={}; queued for the next join",
                    who.uuid(),
                    amount,
                    currencyId);
        } catch (RuntimeException failure) {
            log.error(
                    what + " refund of " + amount + " failed for player " + who.uuid() + "; queued for the next join",
                    failure);
        }
        owe(who, amount, currencyId, what);
    }

    private void owe(PlayerRef who, BigDecimal amount, String currencyId, String what) {
        try {
            owed.orElseThrow().owe(who, amount, currencyId);
        } catch (RuntimeException failure) {
            log.error(
                    what + " refund of " + amount + " " + currencyId + " could not be queued for player " + who.uuid()
                            + "; the player is owed that amount",
                    failure);
        }
    }
}
