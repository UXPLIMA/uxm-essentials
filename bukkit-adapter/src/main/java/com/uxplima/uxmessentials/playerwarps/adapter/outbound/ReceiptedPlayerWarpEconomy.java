package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.shared.adapter.outbound.ChargeReceipts;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * Wraps the player-warps economy seam so every charge it makes is reported to the person who paid. The three
 * charging paths are exactly the ones a player never sees coming: the entry fee is taken as part of a teleport, the
 * sponsorship fee as part of a purchase whose success line says nothing about money, and rent is collected on a
 * timer from an owner who did nothing at all at that moment. Money leaving with no word for it is the same defect
 * as an item that never arrives.
 *
 * <p>A decorator rather than a change to the seam itself, because the seam is implemented against the database in
 * the persistence adapter, where the message catalog has no business being. Every method delegates unchanged and
 * only a successful charge adds the receipt; a refused charge is left to the context's own refusal message, and the
 * credit paths ({@code withdraw}, {@code refund}) are untouched since money arriving is already reported where it
 * matters.
 */
@NullMarked
public final class ReceiptedPlayerWarpEconomy implements PlayerWarpEconomy {

    private final PlayerWarpEconomy delegate;
    private final CurrencyRegistry currencies;
    private final ChargeReceipts receipts;

    public ReceiptedPlayerWarpEconomy(
            PlayerWarpEconomy delegate, CurrencyRegistry currencies, ChargeReceipts receipts) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.receipts = Objects.requireNonNull(receipts, "receipts");
    }

    @Override
    public Result<Unit, ChargeError> chargeAndAccrue(
            PlayerRef payer, PlayerWarpId warp, BigDecimal price, String currencyId) {
        Result<Unit, ChargeError> charged = delegate.chargeAndAccrue(payer, warp, price, currencyId);
        return report(charged, payer, price, currencyId);
    }

    @Override
    public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
        return delegate.canAfford(who, amount, currencyId);
    }

    @Override
    public Result<Unit, ChargeError> withdraw(PlayerWarpId warp, PlayerRef to) {
        return delegate.withdraw(warp, to);
    }

    @Override
    public Result<Unit, ChargeError> collectRent(
            PlayerWarpId warp, PlayerRef owner, BigDecimal amount, String currencyId) {
        Result<Unit, ChargeError> collected = delegate.collectRent(warp, owner, amount, currencyId);
        return report(collected, owner, amount, currencyId);
    }

    @Override
    public Result<Unit, ChargeError> refund(PlayerRef to, BigDecimal amount, String currencyId) {
        return delegate.refund(to, amount, currencyId);
    }

    @Override
    public Result<Unit, ChargeError> chargeOwner(PlayerRef owner, BigDecimal amount, String currencyId) {
        Result<Unit, ChargeError> charged = delegate.chargeOwner(owner, amount, currencyId);
        return report(charged, owner, amount, currencyId);
    }

    /** Add the receipt to a charge that took, and pass every result through untouched. */
    private Result<Unit, ChargeError> report(
            Result<Unit, ChargeError> outcome, PlayerRef payer, BigDecimal amount, String currencyId) {
        if (outcome.isOk()) {
            receipts.charged(payer, Money.of(currency(currencyId), amount), EconomyMessageKey.CHARGE_PLAYERWARP);
        }
        return outcome;
    }

    /** The currency the charge was denominated in, falling back to the default when the id names none. */
    private Currency currency(String currencyId) {
        return currencies.find(CurrencyId.of(currencyId)).orElseGet(currencies::defaultCurrency);
    }
}
