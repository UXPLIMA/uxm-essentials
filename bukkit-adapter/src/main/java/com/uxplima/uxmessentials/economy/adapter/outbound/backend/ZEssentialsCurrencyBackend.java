package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * A named zEssentials economy, reached reflectively off the plugin instance. zEssentials
 * ({@code fr.maxlego08.essentials}) exposes an economy manager from its plugin object ({@code getEconomyManager()});
 * the manager routes by economy name ({@code getBalance(UUID, String)}, {@code deposit(UUID, String, BigDecimal)},
 * {@code withdraw(UUID, String, BigDecimal)}). The id is {@code zessentials:<name>}.
 *
 * <p>The reflective method shape is not contractually stable across zEssentials versions, so this backend makes a
 * clearly-structured best-effort call and lets the base class log-once-and-degrade on any reflective failure. No
 * {@code fr.maxlego08} type is named here: every reference is a string class-name or a reflected member, so the
 * absent path loads nothing.
 */
public final class ZEssentialsCurrencyBackend extends ReflectiveCurrencyBackend {

    private static final String PLUGIN_NAME = "zEssentials";

    public ZEssentialsCurrencyBackend(String name, Server server, Logger log) {
        super("zessentials:" + Objects.requireNonNull(name, "name"), PLUGIN_NAME, name, server, log, Precision.DECIMAL);
    }

    @Override
    protected double readBalance(UUID player) throws ReflectiveOperationException {
        Object manager = economyManager();
        Object money = balanceOf(manager, player);
        return money instanceof Number number ? number.doubleValue() : 0;
    }

    @Override
    protected boolean changeBalance(UUID player, BigDecimal amount, boolean deposit)
            throws ReflectiveOperationException {
        Object manager = economyManager();
        if (!deposit) {
            Object money = balanceOf(manager, player);
            double current = money instanceof Number number ? number.doubleValue() : 0;
            if (current < amount.doubleValue()) {
                return false;
            }
        }
        Object handle = manager.getClass()
                .getMethod(deposit ? "deposit" : "withdraw", UUID.class, String.class, BigDecimal.class)
                .invoke(manager, player, economyName(), amount);
        return !Boolean.FALSE.equals(handle);
    }

    private Object balanceOf(Object manager, UUID player) throws ReflectiveOperationException {
        return manager.getClass()
                .getMethod("getBalance", UUID.class, String.class)
                .invoke(manager, player, economyName());
    }

    /** The configured economy name, falling back to {@code "default"} when none is set. */
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
