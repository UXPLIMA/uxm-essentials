package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.ClickActionEconomy;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the shared {@link ClickActionEconomy} seam to the resolved {@link EconomyProvider}, so a {@code COST}
 * click action charges the clicking viewer in the default currency without the owning context ever importing an
 * economy type (mirrors {@link ProviderWarpEconomy} and {@link ProviderKitEconomy}). This is the adapter that
 * flips the npc {@code Optional<ClickActionEconomy>} from empty to present once economy is wired: a
 * {@code withdraw} is a guarded single-sided debit at the database, so a concurrent spend can never
 * double-charge.
 *
 * <p>A {@code COST} amount is a bare {@link BigDecimal}; this adapter denominates it in the configured default
 * {@link Currency} before charging. {@code withdraw} is the guarded debit whose {@code isOk()} reports whether
 * the funds sufficed — a single call, so the charge happens exactly once.
 */
@NullMarked
public final class ProviderNpcEconomy implements ClickActionEconomy {

    private final EconomyProvider economy;
    private final Currency currency;

    public ProviderNpcEconomy(EconomyProvider economy, Currency currency) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.currency = Objects.requireNonNull(currency, "currency");
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
