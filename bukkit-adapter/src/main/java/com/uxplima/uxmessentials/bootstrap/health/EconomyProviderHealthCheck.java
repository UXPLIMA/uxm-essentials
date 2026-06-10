package com.uxplima.uxmessentials.bootstrap.health;

import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import org.jspecify.annotations.NullMarked;

/**
 * Checks which economy is authoritative for {@code /uxmess doctor}. The economy context runs register-or-defer
 * through the {@code ServicesManager} (docs/11 §4.1): it registers our native {@link EconomyProvider} unless a
 * foreign economy already holds the slot, in which case it consumes the incumbent. Either way every consumer in
 * this plugin reads whoever owns the service registration here.
 *
 * <p>This check reports {@code OK} when our plugin owns the registration (our native ledger is authoritative),
 * {@code WARN} when another plugin owns it (the classic silent failure: a foreign economy won the slot, so our
 * balances are not the source of truth), and {@code WARN} when economy is enabled yet no provider is registered
 * at all. It is wired only when the economy module is enabled, so a disabled economy contributes no line.
 */
@NullMarked
public final class EconomyProviderHealthCheck implements HealthCheck {

    private final ServicesManager services;
    private final Plugin plugin;

    public EconomyProviderHealthCheck(ServicesManager services, Plugin plugin) {
        this.services = Objects.requireNonNull(services, "services");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public String name() {
        return "economy-provider";
    }

    @Override
    public HealthResult check() {
        RegisteredServiceProvider<EconomyProvider> registration = services.getRegistration(EconomyProvider.class);
        if (registration == null) {
            return HealthResult.warn("economy is enabled but no provider is registered");
        }
        Plugin owner = registration.getPlugin();
        if (owner.equals(plugin)) {
            return HealthResult.ok("our native economy provider is authoritative");
        }
        return HealthResult.warn("another plugin owns the economy provider: " + owner.getName());
    }
}
