package com.uxplima.uxmessentials.shared.application.module;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The injection envelope handed to a module's {@link FeatureModule#start(ModuleContext)}.
 *
 * <p>A module never reaches for the plugin instance or news up a port — it receives what it needs
 * here, applying constructor injection at the module seam. For this phase the envelope carries the
 * module's own {@link ModuleId} and its configuration subtree; the shared outbound ports (scheduler,
 * permissions, cooldowns, economy, messages, the in-process event bus, the typed data source) are
 * threaded through here as each bounded context lands.
 *
 * @param moduleId identity of the module being started
 * @param config the configuration store, scoped by convention to the module's {@code configRoot}
 */
public record ModuleContext(ModuleId moduleId, ConfigStore config) {

    public ModuleContext {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(config, "config");
    }
}
