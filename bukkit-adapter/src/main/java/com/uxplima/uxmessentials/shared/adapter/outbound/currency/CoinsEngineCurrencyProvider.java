package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

import java.util.Collection;
import java.util.UUID;

import org.bukkit.Server;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * The {@code coinsengine} back-end, a multi-currency economy reached reflectively. CoinsEngine exposes a static
 * facade {@code su.nightexpress.coinsengine.api.CoinsEngineAPI} keyed by a {@code Currency} resolved from its name:
 * {@code getCurrency(name)} (or the first registered currency for the bare {@code coinsengine} spec), then
 * {@code getBalance(UUID, Currency)} / {@code addBalance(UUID, Currency, double)} /
 * {@code removeBalance(UUID, Currency, double)}.
 *
 * <p>The exact API shape can shift between CoinsEngine versions; any mismatch surfaces as a
 * {@link ReflectiveOperationException} the base class logs once and degrades to a no-op, so a version bump never
 * throws into a menu click. No {@code su.nightexpress} type is named here — the {@code Currency} parameter classes
 * are looked up by string name, so the absent path loads nothing.
 */
final class CoinsEngineCurrencyProvider extends ReflectiveCurrencyProvider {

    private static final String PLUGIN_NAME = "CoinsEngine";
    private static final String API_CLASS = "su.nightexpress.coinsengine.api.CoinsEngineAPI";
    private static final String CURRENCY_CLASS = "su.nightexpress.coinsengine.api.currency.Currency";

    CoinsEngineCurrencyProvider(String id, @Nullable String currency, Server server, Logger log) {
        super(id, PLUGIN_NAME, currency, server, log);
    }

    @Override
    protected double readBalance(UUID player) throws ReflectiveOperationException {
        Class<?> api = Class.forName(API_CLASS);
        Class<?> currencyType = Class.forName(CURRENCY_CLASS);
        Object money = api.getMethod("getBalance", UUID.class, currencyType).invoke(null, player, currency());
        return ((Number) money).doubleValue();
    }

    @Override
    protected boolean changeBalance(UUID player, double amount, boolean deposit) throws ReflectiveOperationException {
        Class<?> api = Class.forName(API_CLASS);
        Class<?> currencyType = Class.forName(CURRENCY_CLASS);
        Object currency = currency();
        if (!deposit) {
            Object money = api.getMethod("getBalance", UUID.class, currencyType).invoke(null, player, currency);
            if (((Number) money).doubleValue() < amount) {
                return false;
            }
        }
        String method = deposit ? "addBalance" : "removeBalance";
        api.getMethod(method, UUID.class, currencyType, double.class).invoke(null, player, currency, amount);
        return true;
    }

    /** The CoinsEngine {@code Currency} for our name, or the first registered one for the default spec. */
    private Object currency() throws ReflectiveOperationException {
        Class<?> facade = Class.forName(API_CLASS);
        if (currency != null) {
            Object resolved = facade.getMethod("getCurrency", String.class).invoke(null, currency);
            if (resolved == null) {
                throw new ReflectiveOperationException("CoinsEngine has no currency named " + currency);
            }
            return resolved;
        }
        Object currencies = facade.getMethod("getCurrencies").invoke(null);
        if (currencies instanceof Collection<?> collection && !collection.isEmpty()) {
            return collection.iterator().next();
        }
        throw new ReflectiveOperationException("CoinsEngine has no registered currency");
    }
}
