package com.uxplima.uxmessentials.customcommands.adapter.outbound;

import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.customcommands.application.port.CommandFee;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.CurrencyProvider;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The price of a custom command, paid through the shared currency facade. The provider is resolved on every call
 * rather than captured once, because the economy module wires after this one: a backend that arrives late is picked
 * up without a restart, the same way a world entry fee resolves its provider lazily.
 *
 * <p>A backend that does not answer is reported through {@link #available()} and never waved through here: whether
 * such a command runs unpaid or is refused is the operator's {@code on-backend-missing} choice, applied by the use
 * case.
 */
public final class CurrenciesCommandFee implements CommandFee {

    private final Currencies currencies;
    private final Supplier<String> currencySpec;

    public CurrenciesCommandFee(Currencies currencies, Supplier<String> currencySpec) {
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.currencySpec = Objects.requireNonNull(currencySpec, "currencySpec");
    }

    @Override
    public boolean available() {
        return provider().available();
    }

    @Override
    public boolean canAfford(PlayerRef who, double amount) {
        Objects.requireNonNull(who, "who");
        if (amount <= 0) {
            return true;
        }
        return provider().has(who.uuid(), amount);
    }

    @Override
    public boolean charge(PlayerRef who, double amount) {
        Objects.requireNonNull(who, "who");
        if (amount <= 0) {
            return true;
        }
        return provider().withdraw(who.uuid(), amount);
    }

    @Override
    public String format(double amount) {
        CurrencyProvider provider = provider();
        return provider.available() ? provider.format(amount) : String.valueOf(amount);
    }

    private CurrencyProvider provider() {
        return currencies.resolve(currencySpec.get());
    }
}
