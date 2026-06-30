package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.Server;

import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.EconomyQuery;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.Hooks;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * The multi-currency façade: maps a spec string to the {@link CurrencyProvider} that serves it. Built once at
 * bootstrap from the resolved {@link Hooks} (for the Vault back-end), the {@link Server} (for Exp and the
 * plugin-present guards) and a configured default currency id, then handed to the menu engine so a Phase-2 economy
 * action or a Phase-3 requirement reaches a back-end with {@code resolve(spec).deposit(...)} and friends.
 *
 * <p>Spec grammar:
 *
 * <ul>
 *   <li>{@code vault} — the server economy through the Vault hook.
 *   <li>{@code exp} — native Paper experience points (online players only).
 *   <li>{@code playerpoints} — PlayerPoints (reflection).
 *   <li>{@code coinsengine} / {@code coinsengine:<name>} — CoinsEngine's default or a named currency (reflection).
 *   <li>{@code zessentials} / {@code zessentials:<name>} — zEssentials' default or a named economy (reflection).
 *   <li>empty / blank — the configured default currency.
 *   <li>anything else — a no-op provider (never available), with one warning logged.
 * </ul>
 *
 * <p>A spec is normalised (trimmed, the back-end head lower-cased, the sub-currency name left as written) so it
 * keys the cache stably; {@link #resolve(String)} is cheap and repeatable (Phase 2/3 call it per click), returning
 * the same provider instance for the same normalised spec. No static state — the instance is constructor-injected.
 */
public final class Currencies {

    private final Hooks hooks;
    private final Server server;
    private final Logger log;
    private final String defaultSpec;
    private final ConcurrentMap<String, CurrencyProvider> cache = new ConcurrentHashMap<>();

    public Currencies(Hooks hooks, Server server, Logger log, String defaultCurrency) {
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
        Objects.requireNonNull(defaultCurrency, "defaultCurrency");
        // A blank configured default would otherwise recurse blank → default → blank; fall back to vault, the
        // out-of-the-box server economy, and document it so a misconfiguration reads as an obvious choice.
        String configured = normalise(defaultCurrency);
        this.defaultSpec = configured.isEmpty() ? "vault" : configured;
    }

    /** The provider for {@code spec}; never null. A blank spec resolves the configured default. Cached per spec. */
    public CurrencyProvider resolve(String spec) {
        Objects.requireNonNull(spec, "spec");
        String normalised = normalise(spec);
        String key = normalised.isEmpty() ? defaultSpec : normalised;
        return cache.computeIfAbsent(key, this::build);
    }

    /** The configured default currency id (already normalised), e.g. {@code vault}. */
    public String defaultCurrency() {
        return defaultSpec;
    }

    private CurrencyProvider build(String spec) {
        int colon = spec.indexOf(':');
        String head = colon < 0 ? spec : spec.substring(0, colon);
        String name = colon < 0 ? null : spec.substring(colon + 1);
        return switch (head) {
            case "vault" -> new VaultCurrencyProvider(spec, hooks.capability(EconomyQuery.class));
            case "exp" -> new ExpCurrencyProvider(spec, server);
            case "playerpoints" -> new PlayerPointsCurrencyProvider(spec, server, log);
            case "coinsengine" -> new CoinsEngineCurrencyProvider(spec, name, server, log);
            case "zessentials" -> new ZEssentialsCurrencyProvider(spec, name, server, log);
            default -> unknown(spec);
        };
    }

    private CurrencyProvider unknown(String spec) {
        log.warn("event=currency_unknown spec={}", spec);
        return CurrencyProvider.unavailable(spec);
    }

    /** Trim, lower-case the back-end head, and keep the sub-currency name verbatim (case can matter to a plugin). */
    private static String normalise(String spec) {
        String trimmed = spec.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int colon = trimmed.indexOf(':');
        if (colon < 0) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        String head = trimmed.substring(0, colon).toLowerCase(Locale.ROOT);
        return head + ":" + trimmed.substring(colon + 1);
    }
}
