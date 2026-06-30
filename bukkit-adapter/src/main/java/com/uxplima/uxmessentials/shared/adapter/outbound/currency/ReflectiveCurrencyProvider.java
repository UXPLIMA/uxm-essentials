package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Server;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Shared scaffolding for the back-ends reached purely by reflection (PlayerPoints, CoinsEngine, zEssentials). A
 * subclass names the Bukkit plugin it integrates with and implements two reflective primitives — read a balance,
 * change a balance — while this base owns the load-safe contract around them: every call is gated by the
 * plugin-present guard, and any {@link ReflectiveOperationException} (the API absent, or its shape shifted under a
 * version bump) is logged exactly once and degraded to a no-op instead of propagating.
 *
 * <p>This is the same discipline the migration {@code PlayerPointsBalanceFeed} uses. Crucially, a subclass names
 * the provider SDK only by string class-name through {@link Class#forName(String)} and reflective lookups, so no
 * field or method signature here carries an SDK type — constructing one of these on a server without the plugin
 * loads none of its classes, and {@link #available()} short-circuits before any reflection runs.
 */
abstract class ReflectiveCurrencyProvider implements CurrencyProvider {

    private final String id;
    private final String pluginName;

    /** The sub-currency name to act on, or {@code null} for the back-end's default currency. */
    protected final @Nullable String currency;

    protected final Server server;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    ReflectiveCurrencyProvider(String id, String pluginName, @Nullable String currency, Server server, Logger log) {
        this.id = Objects.requireNonNull(id, "id");
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
        this.currency = currency;
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final boolean available() {
        return server.getPluginManager().isPluginEnabled(pluginName);
    }

    @Override
    public final double balance(UUID player) {
        Objects.requireNonNull(player, "player");
        if (!available()) {
            return 0;
        }
        try {
            return readBalance(player);
        } catch (ReflectiveOperationException failure) {
            degrade(failure);
            return 0;
        }
    }

    @Override
    public final boolean has(UUID player, double amount) {
        Objects.requireNonNull(player, "player");
        return available() && balance(player) >= amount;
    }

    @Override
    public final boolean withdraw(UUID player, double amount) {
        Objects.requireNonNull(player, "player");
        return change(player, amount, false);
    }

    @Override
    public final boolean deposit(UUID player, double amount) {
        Objects.requireNonNull(player, "player");
        return change(player, amount, true);
    }

    @Override
    public String format(double amount) {
        return CurrencyAmounts.plain(amount);
    }

    private boolean change(UUID player, double amount, boolean deposit) {
        if (!available()) {
            return false;
        }
        try {
            return changeBalance(player, amount, deposit);
        } catch (ReflectiveOperationException failure) {
            degrade(failure);
            return false;
        }
    }

    /** Read {@code player}'s balance in {@link #currency} reflectively; called only past the present-guard. */
    protected abstract double readBalance(UUID player) throws ReflectiveOperationException;

    /** Add (deposit) or remove (withdraw) {@code amount} reflectively; called only past the present-guard. */
    protected abstract boolean changeBalance(UUID player, double amount, boolean deposit)
            throws ReflectiveOperationException;

    /** Log the first reflective failure for this provider; subsequent ones stay quiet to avoid log spam. */
    private void degrade(ReflectiveOperationException failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=currency_reflection_failed provider={} reason={}", id(), failure.toString());
        }
    }
}
