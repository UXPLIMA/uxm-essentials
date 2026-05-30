package com.uxplima.uxmessentials.economy.adapter;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

import org.bukkit.plugin.ServicePriority;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * Reads the economy module's configuration subtree into the typed values the wiring needs: the
 * {@link CurrencyRegistry} (the closed currency set with its single default), the
 * {@code ServicePriority} for register-or-defer, the {@code /pay} confirm timeout and toggle default, the
 * baltop page size / cache TTL / exempt node, and the persistence debounce/flush windows.
 *
 * <p>A fresh install ships exactly one currency — the configured default with a sensible symbol/format/
 * precision — and every command that omits {@code [currency]} resolves to it. Operators declare more under
 * {@code currencies.<id>}; this reader builds only the default currency from the well-known keys, with the
 * additional-currency map a documented follow-up that needs a structured-list config read not on the narrow
 * {@link ConfigStore} contract (the registry, port, and commands already carry a multi-currency set, so
 * adding more is a config-read change, not a model change).
 */
@NullMarked
public final class EconomyConfig {

    private final ConfigStore config;

    public EconomyConfig(ConfigStore config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Build the closed currency registry; ships a single configured default out of the box. */
    public CurrencyRegistry currencies() {
        CurrencyId defaultId = CurrencyId.of(config.getString("wallet.default-currency", "coins"));
        Currency.Builder builder = Currency.builder(defaultId)
                .symbol(config.getString("currencies." + defaultId.value() + ".symbol", "$"))
                .plural(config.getString("currencies." + defaultId.value() + ".plural", defaultId.value()))
                .format(config.getString("currencies." + defaultId.value() + ".format", "#,##0.00"))
                .precision(config.getInt("currencies." + defaultId.value() + ".precision", 2))
                .starting(decimal("currencies." + defaultId.value() + ".starting", "wallet.starting-balance", "0"))
                .min(decimal("currencies." + defaultId.value() + ".min-balance", "wallet.min-balance", "0"))
                .max(decimal("currencies." + defaultId.value() + ".max-balance", "wallet.max-balance", "1000000000000"))
                .minPay(decimal("currencies." + defaultId.value() + ".min-pay", "pay.min-pay", "0.01"));
        BigDecimal confirm = optionalConfirmThreshold(defaultId);
        if (confirm != null) {
            builder.confirmThreshold(confirm);
        }
        return CurrencyRegistry.single(builder.build());
    }

    /** The {@code ServicePriority} the native provider registers at; {@code Normal} unless raised on purpose. */
    public ServicePriority registerPriority() {
        String raw = config.getString("provider.priority", "Normal");
        try {
            return ServicePriority.valueOf(capitalise(raw));
        } catch (IllegalArgumentException unknown) {
            return ServicePriority.Normal;
        }
    }

    /** Whether to register the native provider at all (the register-or-defer entry condition). */
    public boolean registerProvider() {
        return config.getBoolean("provider.register", true);
    }

    /** The default accept-pay flag a player takes before they ever run {@code /paytoggle}. */
    public boolean payToggleDefault() {
        return config.getBoolean("pay.toggle-default", true);
    }

    /** The {@code /payconfirm} prompt timeout. */
    public Duration confirmTimeout() {
        return Duration.ofMillis(Math.max(1_000L, config.getLong("pay.confirm-timeout-ms", 30_000L)));
    }

    /** The {@code /baltop} page size, clamped to the use case's ceiling by the use case itself. */
    public int baltopPageSize() {
        return Math.max(1, config.getInt("baltop.page-size", 10));
    }

    /** The per-currency baltop snapshot refresh interval. */
    public Duration baltopCacheTtl() {
        return Duration.ofMillis(Math.max(1_000L, config.getLong("baltop.cache-ttl-ms", 60_000L)));
    }

    /** The number of rows each baltop snapshot retains. */
    public int baltopCapacity() {
        return Math.max(baltopPageSize(), config.getInt("baltop.snapshot-capacity", 100));
    }

    /** The permission node whose holders are excluded from every leaderboard. */
    public String baltopExemptNode() {
        return config.getString("baltop.exempt-permission", "uxmessentials.economy.baltop.exempt");
    }

    /** The debounced-settle window. */
    public Duration writeDebounce() {
        return Duration.ofMillis(Math.max(50L, config.getLong("persistence.write-debounce-ms", 250L)));
    }

    /** The append-only telemetry batch-flush interval. */
    public Duration batchFlush() {
        return Duration.ofMillis(Math.max(100L, config.getLong("persistence.batch-flush-ms", 1_000L)));
    }

    private @org.jspecify.annotations.Nullable BigDecimal optionalConfirmThreshold(CurrencyId id) {
        String raw = config.getString("currencies." + id.value() + ".confirm-threshold", "");
        String fallback = config.getString("pay.confirm-threshold", "");
        String chosen = raw.isEmpty() ? fallback : raw;
        if (chosen.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(chosen.strip());
            // A configured -1 disables confirmation for the currency (the doc's sentinel).
            return value.signum() < 0 ? null : value;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private BigDecimal decimal(String specificPath, String fallbackPath, String hardDefault) {
        String raw = config.getString(specificPath, "");
        if (raw.isEmpty()) {
            raw = config.getString(fallbackPath, hardDefault);
        }
        try {
            return new BigDecimal(raw.strip());
        } catch (NumberFormatException notANumber) {
            return new BigDecimal(hardDefault);
        }
    }

    private static String capitalise(String value) {
        String trimmed = value.strip();
        return trimmed.isEmpty() ? trimmed : Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }
}
