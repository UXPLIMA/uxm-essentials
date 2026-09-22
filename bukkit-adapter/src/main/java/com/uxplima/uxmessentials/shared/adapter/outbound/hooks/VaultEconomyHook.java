package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import org.bukkit.Server;

import com.uxplima.uxmlib.hook.Integration;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link Integration} for Vault's economy: it integrates with the {@code Vault} plugin and resolves to an
 * {@link EconomyQuery}. It names {@link VaultEconomyService} only inside {@link #whenPresent}, and that service
 * is the single class importing {@code net.milkbowl.vault.economy}; so on a server without Vault the service is
 * never constructed and the SDK is never loaded: {@code Integrations} hands callers the no-op {@link EconomyQuery#ABSENT}.
 */
@NullMarked
public final class VaultEconomyHook implements Integration<EconomyQuery> {

    private static final String PLUGIN_NAME = "Vault";

    @Override
    public String pluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public Class<EconomyQuery> capability() {
        return EconomyQuery.class;
    }

    @Override
    public EconomyQuery whenAbsent() {
        return EconomyQuery.ABSENT;
    }

    @Override
    public EconomyQuery whenPresent(Server server) {
        // The single reference to the SDK-touching service, reached only past Integrations' present-guard.
        return new VaultEconomyService(server);
    }
}
