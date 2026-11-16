package com.uxplima.uxmessentials.shared.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * Shared bridge from a feature context's narrow per-context economy seam (the ranks {@code RankEconomy}, the warps
 * {@code WarpEconomy}, the homes {@code HomeEconomy}, ...) to the resolved {@link EconomyProvider}. Each seam is
 * charged in a resolved {@link Currency} without the feature context ever importing an economy type.
 * A subclass {@code implements} its own port and inherits the two
 * bridge operations from here.
 *
 * <p>A per-context cost is a bare {@link BigDecimal} in the feature's own terms; this bridge denominates it in the
 * resolved currency before charging. {@link #canAfford} is a balance read and {@link #withdraw} is a guarded
 * single-sided debit at the database whose {@code isOk()} reports whether the funds sufficed, so a concurrent
 * spend can never double-charge.
 *
 * <p>A subclass whose seam adds a credit or a two-sided move (a deposit, a transfer) composes the protected
 * {@link #economy()} provider with the shared {@link #resolve(String)} currency resolution, so the constructor and
 * the resolve/charge logic still live here once.
 */
@NullMarked
public abstract class AbstractProviderEconomy {

    private final EconomyProvider economy;
    private final Currency currency;
    private final Optional<ChargeReceipts> receipts;

    protected AbstractProviderEconomy(EconomyProvider economy, Currency currency) {
        this(economy, currency, Optional.empty());
    }

    protected AbstractProviderEconomy(EconomyProvider economy, Currency currency, Optional<ChargeReceipts> receipts) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.receipts = Objects.requireNonNull(receipts, "receipts");
    }

    /** True when {@code who} can afford {@code amount} in the resolved currency (a read, no money moves). */
    public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        Currency target = resolve(currencyId);
        return !economy.balance(who, target).isLessThan(Money.of(target, amount));
    }

    /**
     * Guarded single-sided debit; {@code true} means the funds sufficed and the money left exactly once. A debit that
     * takes is reported to the payer through {@link ChargeReceipts}: the feature's own success line says nothing
     * about the money, so without the receipt the charge is invisible until someone checks their balance.
     */
    public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
        return debit(who, amount, currencyId).isPresent();
    }

    /**
     * {@link #withdraw}, answering the id of the currency the money left in ({@code "default"} resolved to this
     * server's), or empty when the funds did not suffice. A seam that may refund later records this id, so the refund
     * names the currency itself rather than whatever {@code "default"} means where and when it runs.
     */
    public Optional<String> debit(PlayerRef who, BigDecimal amount, String currencyId) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        Currency target = resolve(currencyId);
        Money charge = Money.of(target, amount);
        if (economy.debit(who, charge).isErr()) {
            return Optional.empty();
        }
        receipts.ifPresent(receipt -> receipt.charged(who, charge, chargeLabel()));
        return Optional.of(target.id().value());
    }

    /**
     * Give {@code amount} back to {@code who} in exactly the currency {@code currencyId} names, the id {@link #debit}
     * answered. False when this server knows no such currency, so the money is never credited in another one. A
     * charge recorded as {@code "default"} by a release that did not resolve it is given back in this server's
     * default, the only reading such a record has.
     */
    protected boolean refundExactly(PlayerRef who, BigDecimal amount, String currencyId) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currencyId, "currencyId");
        return named(currencyId)
                .map(c -> economy.credit(who, Money.of(c, amount)).isOk())
                .orElse(false);
    }

    /**
     * The catalog label naming what this seam's charges are for, slotted into the receipt as {@code {what}}. Each
     * subclass answers for its own feature ("a warp", "a kit"), which is what keeps the receipt sentence itself a
     * single shared catalog entry.
     */
    protected abstract MessageKey chargeLabel();

    /** The resolved provider, for a subclass seam that credits or transfers on top of the shared charge logic. */
    protected EconomyProvider economy() {
        return economy;
    }

    /**
     * Resolves {@code currencyId} to a {@link Currency}: the configured default for {@code "default"}, an
     * id-matched currency from the provider's set otherwise, falling back to the default when the id names none.
     */
    protected Currency resolve(String currencyId) {
        Objects.requireNonNull(currencyId, "currencyId");
        return named(currencyId).orElse(currency);
    }

    /** The currency {@code currencyId} names, {@code "default"} being this server's; empty for an id it does not know. */
    private Optional<Currency> named(String currencyId) {
        if (currencyId.equalsIgnoreCase("default")) {
            return Optional.of(currency);
        }
        return economy.currencies().stream()
                .filter(c -> c.id().value().equalsIgnoreCase(currencyId))
                .findFirst();
    }
}
