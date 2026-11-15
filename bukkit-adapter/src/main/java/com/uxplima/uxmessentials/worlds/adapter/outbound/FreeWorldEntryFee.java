package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.port.WorldEntryFee;
import org.jspecify.annotations.NullMarked;

/**
 * The no-op {@link WorldEntryFee} wired in when the economy module is disabled, making every world free to
 * enter: it never reads a balance and never charges, so {@link #canAfford} and {@link #charge} always
 * succeed, and a refund of anything but nothing fails. Supplying this lets the worlds context depend on the fee
 * port unconditionally and never branch on whether economy is present.
 */
@NullMarked
public final class FreeWorldEntryFee implements WorldEntryFee {

    /** Nothing is ever taken, so the id only labels a zero charge that is never refunded. */
    static final String FREE_CURRENCY = "default";

    @Override
    public boolean canAfford(PlayerRef who, BigDecimal amount) {
        return true;
    }

    @Override
    public boolean charge(PlayerRef who, BigDecimal amount) {
        return true;
    }

    @Override
    public String currencyId() {
        return FREE_CURRENCY;
    }

    /**
     * Nothing to credit with: only a zero amount is given back in full. Answering true for any other amount would tell
     * the player a fee taken while an economy ran came back when it did not.
     */
    @Override
    public boolean refund(PlayerRef who, BigDecimal amount, String currencyId) {
        return amount.signum() == 0;
    }
}
