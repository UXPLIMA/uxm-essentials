package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.port.WorldEntryFee;
import org.jspecify.annotations.Nullable;

/**
 * A {@link WorldEntryFee} that resolves its backing implementation lazily, on each call. The economy module
 * is enabled after worlds, so at worlds-wire time the provider and currency are not yet available; the
 * composition root captures them onto its cross-context links and hands their suppliers here. Each charge
 * resolves to the live {@link EconomyWorldEntryFee} once economy is up, or to the free implementation when
 * economy is disabled (the supplier stays null), so the worlds context never branches on economy presence
 * and the wiring stays order-independent.
 */
public final class LinkedWorldEntryFee implements WorldEntryFee {

    private final Supplier<@Nullable EconomyProvider> provider;
    private final Supplier<@Nullable Currency> currency;

    public LinkedWorldEntryFee(Supplier<@Nullable EconomyProvider> provider, Supplier<@Nullable Currency> currency) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    @Override
    public boolean canAfford(PlayerRef who, BigDecimal amount) {
        return resolve().canAfford(who, amount);
    }

    @Override
    public boolean charge(PlayerRef who, BigDecimal amount) {
        return resolve().charge(who, amount);
    }

    private WorldEntryFee resolve() {
        EconomyProvider live = provider.get();
        Currency unit = currency.get();
        return live != null && unit != null ? new EconomyWorldEntryFee(live, unit) : new FreeWorldEntryFee();
    }
}
