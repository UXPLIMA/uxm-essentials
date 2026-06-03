package com.uxplima.uxmessentials.economy.fakes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.WorthOverrideStore;

/**
 * An in-memory {@link WorthOverrideStore} for the use-case tests, case-folding the material id to its
 * lowercase form exactly as the jOOQ store does, so a test exercises the same lookup contract without a
 * database.
 */
public final class InMemoryWorthOverrideStore implements WorthOverrideStore {

    private final Map<String, BigDecimal> prices = new HashMap<>();

    @Override
    public void set(String material, BigDecimal price) {
        prices.put(key(material), Objects.requireNonNull(price, "price"));
    }

    @Override
    public Optional<BigDecimal> find(String material) {
        return Optional.ofNullable(prices.get(key(material)));
    }

    @Override
    public boolean exists(String material) {
        return prices.containsKey(key(material));
    }

    @Override
    public boolean remove(String material) {
        return prices.remove(key(material)) != null;
    }

    private static String key(String material) {
        return Objects.requireNonNull(material, "material").toLowerCase(Locale.ROOT);
    }
}
