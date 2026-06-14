package com.uxplima.uxmessentials.vaults.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultEconomy;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the vaults context's narrow {@link VaultEconomy} seam to the resolved {@link EconomyProvider}, so a
 * per-action vault fee can be charged — and a delete refund paid — in the server's default currency without
 * the vaults context ever importing an economy type (docs/11-economy-integration.md §4.2). This adapter is
 * constructed only when economy is wired <em>and</em> the vaults config opts in; when economy is absent the
 * wiring hands {@link VaultEconomy#NONE} to {@code VaultCharge} instead, so no charge ever fires on a server
 * without economy. Mirrors the homes {@code ProviderHomeEconomy}.
 *
 * <p>A vault cost is a bare {@link BigDecimal} in vaults' own terms; this adapter denominates it in the
 * configured default {@link Currency} before charging. {@code canAfford} is a balance read, {@code withdraw}
 * is the guarded debit (the {@code canAfford} check in {@code VaultCharge} precedes it), and {@code deposit}
 * is the single-sided credit that pays the delete refund back.
 */
@NullMarked
public final class ProviderVaultEconomy implements VaultEconomy {

    private final EconomyProvider economy;
    private final Currency currency;

    public ProviderVaultEconomy(EconomyProvider economy, Currency currency) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    @Override
    public boolean canAfford(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        return !economy.balance(who, currency).isLessThan(Money.of(currency, amount));
    }

    @Override
    public void withdraw(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        // VaultCharge gates this behind canAfford, so the debit only fires when the funds are present; the
        // guarded provider still rejects rather than going negative, which is the double-spend-safe path.
        economy.debit(who, Money.of(currency, amount));
    }

    @Override
    public void deposit(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        economy.credit(who, Money.of(currency, amount));
    }
}
