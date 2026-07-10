package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The menu vocabulary's {@code UUID}/{@code double} surface, served by a {@link CurrencyBackend}. Every
 * {@code give-money} / {@code has-money} click now spends through the same backend a warp fee does, so a
 * native currency picks up the guarded debit and the transaction ledger it never had on this path.
 *
 * <p>The {@code double} boundary is the menu vocabulary's, not ours: amounts are converted to {@link Money}
 * with the currency's precision before they reach the backend, and back again only for display.
 */
public final class BackedCurrencyProvider implements CurrencyProvider {

    private final String spec;
    private final CurrencyBackend backend;
    private final Currency currency;

    public BackedCurrencyProvider(String spec, CurrencyBackend backend, Currency currency) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    @Override
    public String id() {
        return spec;
    }

    @Override
    public boolean available() {
        return backend.available();
    }

    @Override
    public double balance(UUID player) {
        return backend.balance(ref(player), currency).amount().doubleValue();
    }

    @Override
    public boolean has(UUID player, double amount) {
        return backend.balance(ref(player), currency).amount().compareTo(BigDecimal.valueOf(amount)) >= 0;
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        return backend.debit(ref(player), money(amount)).isOk();
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        return backend.credit(ref(player), money(amount)).isOk();
    }

    @Override
    public String format(double amount) {
        return CurrencyAmounts.plain(amount);
    }

    private Money money(double amount) {
        return Money.of(currency, BigDecimal.valueOf(amount));
    }

    // The façade keys players by UUID only; the backend addresses them by PlayerRef, whose identity is the UUID
    // alone, so a name-less ref carries every bit of identity the balance and ledger writes need.
    private static PlayerRef ref(UUID player) {
        return new PlayerRef(Objects.requireNonNull(player, "player"), "");
    }
}
