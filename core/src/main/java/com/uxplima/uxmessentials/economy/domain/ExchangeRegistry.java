package com.uxplima.uxmessentials.economy.domain;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry holding all active exchange rates for the economy.
 */
public final class ExchangeRegistry {

    private final Map<RateKey, ExchangeRate> rates;

    public ExchangeRegistry(Collection<ExchangeRate> exchangeRates) {
        Objects.requireNonNull(exchangeRates, "exchangeRates");
        this.rates = new HashMap<>();
        for (ExchangeRate rate : exchangeRates) {
            rates.put(new RateKey(rate.source(), rate.target()), rate);
        }
    }

    /** Find the exchange rate between the specified source and target currencies. */
    public Optional<ExchangeRate> findRate(CurrencyId source, CurrencyId target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        return Optional.ofNullable(rates.get(new RateKey(source, target)));
    }

    private record RateKey(CurrencyId source, CurrencyId target) {}
}
