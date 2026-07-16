package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.port.TradeEconomy;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the trade context's narrow {@link TradeEconomy} seam to the resolved {@link EconomyProvider}, so a trade's
 * staked money moves without the trade context ever importing an economy type (mirrors {@link ProviderKitEconomy} /
 * {@link ProviderRankEconomy}). This is the adapter that flips the trade {@code Optional<TradeEconomy>} from empty to
 * present once economy is wired.
 *
 * <p>A staked amount is a bare {@link BigDecimal} in a currency id; this adapter denominates it in the matching
 * {@link Currency} (falling back to the configured default) before charging. {@code canAfford} is a balance read for
 * the window preview; {@link #transfer} is the guarded, atomic two-sided move — it never partially applies, so two
 * concurrent settlements can never both overdraw, and a payer who cannot cover the leg is reported by a {@code false}
 * return rather than a check-then-charge race.
 */
@NullMarked
public final class ProviderTradeEconomy implements TradeEconomy {

    private final EconomyProvider economy;
    private final Currency defaultCurrency;

    public ProviderTradeEconomy(EconomyProvider economy, Currency defaultCurrency) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency, "defaultCurrency");
    }

    @Override
    public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        Currency target = resolve(currencyId);
        return !economy.balance(who, target).isLessThan(Money.of(target, amount));
    }

    @Override
    public boolean transfer(PlayerRef from, PlayerRef to, BigDecimal amount, String currencyId) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
        Currency target = resolve(currencyId);
        return economy.transfer(from, to, Money.of(target, amount)).isOk();
    }

    private Currency resolve(String currencyId) {
        Objects.requireNonNull(currencyId, "currencyId");
        if (currencyId.equalsIgnoreCase("default")) {
            return defaultCurrency;
        }
        return economy.currencies().stream()
                .filter(c -> c.id().value().equalsIgnoreCase(currencyId))
                .findFirst()
                .orElse(defaultCurrency);
    }
}
