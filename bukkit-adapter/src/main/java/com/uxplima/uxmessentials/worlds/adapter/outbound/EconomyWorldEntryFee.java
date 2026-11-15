package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.port.WorldEntryFee;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link WorldEntryFee} backed by the live economy. This is the one worlds class that bridges into
 * {@code economy.*}: it reads a player's balance and debits the per-world fee through the shared
 * {@link EconomyProvider} port, in the single currency the worlds module is wired with. The worlds domain
 * and application stay free of economy types; only this outbound adapter touches them.
 */
@NullMarked
public final class EconomyWorldEntryFee implements WorldEntryFee {

    private final EconomyProvider provider;
    private final Currency currency;

    public EconomyWorldEntryFee(EconomyProvider provider, Currency currency) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    @Override
    public boolean canAfford(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        return provider.balance(who, currency).amount().compareTo(amount) >= 0;
    }

    @Override
    public boolean charge(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        return provider.debit(who, Money.of(currency, amount)).isOk();
    }

    @Override
    public String currencyId() {
        return currency.id().value();
    }

    /**
     * Credit back in the currency the fee was taken in, which the hop carried. A backend's fee currency can differ from
     * the origin's, or change on a reload, so crediting this backend's own currency could pay out money that was never
     * taken. A currency this backend does not know is not credited at all: false lets the hop log the charge it could
     * not give back.
     */
    @Override
    public boolean refund(PlayerRef who, BigDecimal amount, String currencyId) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currencyId, "currencyId");
        Optional<Currency> charged = provider.currencies().stream()
                .filter(known -> known.id().value().equalsIgnoreCase(currencyId))
                .findFirst();
        return charged.isPresent()
                && provider.credit(who, Money.of(charged.get(), amount)).isOk();
    }
}
