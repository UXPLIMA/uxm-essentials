package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

import java.math.BigDecimal;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * The {@code zessentials} back-end, a multi-currency economy reached reflectively off the plugin instance.
 * zEssentials ({@code fr.maxlego08.essentials}) exposes an economy manager from its plugin object
 * ({@code getEconomyManager()}); the manager routes by economy name ({@code getEconomy(name)}, or the default for
 * the bare {@code zessentials} spec) and reads/moves a player's balance as a {@link BigDecimal}.
 *
 * <p>The reflective method shape is not contractually stable across zEssentials versions, so this provider makes a
 * clearly-structured best-effort call and lets the base class log-once-and-no-op on any
 * {@link ReflectiveOperationException} — a mismatch degrades gracefully rather than throwing into a click. No
 * {@code fr.maxlego08} type is named here: every reference is a string class-name or a reflected member, so the
 * absent path loads nothing.
 */
final class ZEssentialsCurrencyProvider extends ReflectiveCurrencyProvider {

    private static final String PLUGIN_NAME = "zEssentials";

    ZEssentialsCurrencyProvider(String id, @Nullable String currency, Server server, Logger log) {
        super(id, PLUGIN_NAME, currency, server, log);
    }

    @Override
    protected double readBalance(UUID player) throws ReflectiveOperationException {
        Object manager = economyManager();
        Object money = invokeNamed(manager, "getBalance", player);
        return money instanceof Number number ? number.doubleValue() : 0;
    }

    @Override
    protected boolean changeBalance(UUID player, double amount, boolean deposit) throws ReflectiveOperationException {
        Object manager = economyManager();
        if (!deposit) {
            Object money = invokeNamed(manager, "getBalance", player);
            double current = money instanceof Number number ? number.doubleValue() : 0;
            if (current < amount) {
                return false;
            }
        }
        Object handle = manager.getClass()
                .getMethod(deposit ? "deposit" : "withdraw", UUID.class, String.class, BigDecimal.class)
                .invoke(manager, player, economyName(), BigDecimal.valueOf(amount));
        return !Boolean.FALSE.equals(handle);
    }

    private Object invokeNamed(Object manager, String method, UUID player) throws ReflectiveOperationException {
        return manager.getClass().getMethod(method, UUID.class, String.class).invoke(manager, player, economyName());
    }

    /** The configured economy name, falling back to {@code "default"} for the bare {@code zessentials} spec. */
    private String economyName() {
        return currency != null ? currency : "default";
    }

    private Object economyManager() throws ReflectiveOperationException {
        Plugin plugin = server.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null) {
            throw new ReflectiveOperationException("zEssentials plugin instance unavailable");
        }
        Object manager = plugin.getClass().getMethod("getEconomyManager").invoke(plugin);
        if (manager == null) {
            throw new ReflectiveOperationException("zEssentials economy manager unavailable");
        }
        return manager;
    }
}
