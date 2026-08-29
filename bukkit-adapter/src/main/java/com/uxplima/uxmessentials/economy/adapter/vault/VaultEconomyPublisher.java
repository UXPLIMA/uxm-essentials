package com.uxplima.uxmessentials.economy.adapter.vault;

import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import net.milkbowl.vault.economy.Economy;
import org.jspecify.annotations.NullMarked;

/**
 * Registers {@link NativeVaultEconomy} with the {@code ServicesManager}, and takes it back on disable. The
 * one place that names the Vault service on the publishing side, so nothing above the {@code vault} package
 * has to import the SDK ({@code economyDomainHasNoProviderSdk}).
 *
 * <p>It defers the same way {@code EconomyProviderRegistrar} does. A server that already runs a real economy
 * plugin keeps it: two providers on one interface means whichever loaded first wins every lookup, and a
 * reward would be paid out of a ledger nobody is reading.
 *
 * <p>Whether Vault is installed at all is not asked here. {@code ForeignEconomyProviders} owns that probe,
 * for the consuming direction and this one alike, so the plugin is named in one file
 * ({@code SoftDependSeamDriftTest}). Reaching either method means the check has already passed.
 */
@NullMarked
public final class VaultEconomyPublisher {

    private VaultEconomyPublisher() {}

    /** Publish the native wallet as the server's Vault economy, unless a real economy already serves it. */
    public static void publish(
            Plugin plugin, EconomyProvider provider, Currency currency, ServicePriority priority, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(log, "log");
        ServicesManager services = plugin.getServer().getServicesManager();
        RegisteredServiceProvider<Economy> existing = services.getRegistration(Economy.class);
        if (existing != null) {
            log.info("event=vault_economy_deferred to={}", existing.getPlugin().getName());
            return;
        }
        services.register(
                Economy.class, new NativeVaultEconomy(provider, currency, plugin.getServer()), plugin, priority);
        log.info(
                "event=vault_economy_registered currency={} priority={}",
                currency.id().value(),
                priority);
    }

    /** Drop this plugin's Vault registration on disable, so a reload publishes cleanly rather than twice. */
    public static void withdraw(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        ServicesManager services = plugin.getServer().getServicesManager();
        for (RegisteredServiceProvider<Economy> registration : services.getRegistrations(Economy.class)) {
            if (registration.getPlugin().equals(plugin)) {
                services.unregister(Economy.class, registration.getProvider());
            }
        }
    }
}
