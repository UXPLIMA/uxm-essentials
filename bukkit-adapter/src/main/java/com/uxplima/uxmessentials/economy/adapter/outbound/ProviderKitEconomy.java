package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the kits context's narrow {@link KitEconomy} seam to the resolved {@link EconomyProvider}, so a
 * per-kit cost is charged in the default currency without the kits context ever importing an economy type
 * (mirrors {@link ProviderWarpEconomy}). This is the adapter that flips the kits {@code Optional<KitEconomy>}
 * from empty to present once economy is wired: a {@code withdraw} is a guarded single-sided debit at the
 * database, so a concurrent spend can never double-charge.
 *
 * <p>A kit cost is a bare {@link BigDecimal} in kits' own terms; this adapter denominates it in the
 * configured default {@link Currency} before charging. {@code canAfford} is a balance read and
 * {@code withdraw} is the guarded debit whose {@code isOk()} reports whether the funds sufficed.
 */
@NullMarked
public final class ProviderKitEconomy implements KitEconomy {

    private final EconomyProvider economy;
    private final Currency currency;

    public ProviderKitEconomy(EconomyProvider economy, Currency currency) {
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
    public boolean withdraw(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        return economy.debit(who, Money.of(currency, amount)).isOk();
    }
}
