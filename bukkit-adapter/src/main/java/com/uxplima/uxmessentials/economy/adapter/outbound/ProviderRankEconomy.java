package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the ranks context's narrow {@link RankEconomy} seam to the resolved {@link EconomyProvider}, so a
 * rank's rankup cost is charged in the default currency without the ranks context ever importing an economy type
 * (mirrors {@link ProviderKitEconomy}). This is the adapter that flips the ranks {@code Optional<RankEconomy>}
 * from empty to present once economy is wired: a {@code withdraw} is a guarded single-sided debit at the
 * database, so a concurrent spend can never double-charge.
 *
 * <p>A rank cost is a bare {@link BigDecimal} in ranks' own terms; this adapter denominates it in the configured
 * default {@link Currency} before charging. {@code canAfford} is a balance read and {@code withdraw} is the
 * guarded debit whose {@code isOk()} reports whether the funds sufficed.
 */
@NullMarked
public final class ProviderRankEconomy implements RankEconomy {

    private final EconomyProvider economy;
    private final Currency currency;

    public ProviderRankEconomy(EconomyProvider economy, Currency currency) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    @Override
    public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        Currency target = resolve(currencyId);
        return !economy.balance(who, target).isLessThan(Money.of(target, amount));
    }

    @Override
    public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        Currency target = resolve(currencyId);
        return economy.debit(who, Money.of(target, amount)).isOk();
    }

    private Currency resolve(String currencyId) {
        Objects.requireNonNull(currencyId, "currencyId");
        if (currencyId.equalsIgnoreCase("default")) {
            return currency;
        }
        return economy.currencies().stream()
                .filter(c -> c.id().value().equalsIgnoreCase(currencyId))
                .findFirst()
                .orElse(currency);
    }
}
