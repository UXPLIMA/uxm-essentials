package com.uxplima.uxmessentials.bootstrap.di;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.EconomyModule;
import com.uxplima.uxmessentials.homes.application.HomesModule;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.teleport.application.TeleportModule;
import com.uxplima.uxmessentials.warps.application.WarpsModule;
import org.jspecify.annotations.NullMarked;

/**
 * The single registration site for every {@link FeatureModule}.
 *
 * <p>Registration is explicit and ordered dependency-first (prerequisites before dependents):
 * {@code economy} before {@code warps} because a warp may charge a cost through the economy
 * provider, {@code teleport} before {@code homes} and {@code warps} because they delegate teleport
 * execution. No bounded context has shipped yet, so the list is currently empty; each context adds
 * exactly one {@code register(...)} line at its dependency-correct position when it lands. The
 * registry stays the greppable, authoritative answer to "which contexts exist?".
 */
@NullMarked
public final class DefaultModuleRegistry implements ModuleRegistry {

    private final ModuleRegistry delegate;

    public DefaultModuleRegistry() {
        this.delegate = new ListModuleRegistry();
        // Feature modules register here in dependency-first order as each context lands. teleport owns
        // all movement orchestration, so it lands before the homes/warps contexts that delegate here.
        // economy registers before warps because a warp may charge a per-warp cost through the economy
        // provider; the economy WarpEconomy bridge is captured during economy wiring and handed to warps.
        delegate.register(new TeleportModule());
        delegate.register(new HomesModule());
        delegate.register(new EconomyModule());
        delegate.register(new WarpsModule());
        //   … through VaultsModule. The shared kernel is not a module and never appears here.
    }

    @Override
    public ModuleRegistry register(FeatureModule module) {
        return delegate.register(module);
    }

    @Override
    public List<FeatureModule> all() {
        return delegate.all();
    }

    @Override
    public Optional<FeatureModule> byId(ModuleId id) {
        return delegate.byId(id);
    }

    @Override
    public List<FeatureModule> enabledModules(com.uxplima.uxmessentials.shared.application.port.ConfigStore config) {
        return delegate.enabledModules(config);
    }
}
