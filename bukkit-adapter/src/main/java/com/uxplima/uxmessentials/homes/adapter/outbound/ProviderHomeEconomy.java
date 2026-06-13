package com.uxplima.uxmessentials.homes.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.homes.application.port.HomeEconomy;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the homes context's narrow {@link HomeEconomy} seam to the resolved {@link EconomyProvider}, so a
 * per-action {@code HomeCost} is charged in the configured currency without the homes context ever importing
 * an economy type ({@code docs/11-economy-integration.md} §4.2). This adapter flips the homes
 * {@code Optional<HomeEconomy>} from empty to present once economy is wired; when it is absent the cost is
 * recorded in config but ignored at use time, so no charge ever fires on a server without economy.
 *
 * <p>A home cost is a bare {@link BigDecimal} in homes' own terms; this adapter denominates it in the
 * resolved {@link Currency} before charging. {@code canAfford} is a balance read and {@code withdraw} is
 * the guarded debit whose {@code isOk()} reports whether the funds sufficed.
 */
@NullMarked
public final class ProviderHomeEconomy implements HomeEconomy {

    private final EconomyProvider economy;
    private final Currency currency;

    public ProviderHomeEconomy(EconomyProvider economy, Currency currency) {
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
