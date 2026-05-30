package com.uxplima.uxmessentials.bootstrap.di;

import java.util.Optional;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

import com.uxplima.uxmessentials.bootstrap.command.UxmessCommand;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.LoadCondition;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The single hand-rolled DI site. Consults the {@link ModuleRegistry} so a disabled module wires
 * nothing — no adapters, no commands, no listeners, no migrations, no runtime state.
 *
 * <p>The wiring invariant: the only path to constructing a context's adapters is through its enabled
 * {@code FeatureModule}. Nothing here news up a context's classes directly; the loop starts each
 * enabled module and the module owns its own construction inside {@code start}. The {@code JavaPlugin}
 * is held only here, in bootstrap — it never leaks into application or adapter code.
 */
@NullMarked
public final class PluginModule {

    private PluginModule() {}

    /** Wires the plugin and returns the resources to close on disable. */
    public static CloseableResources wire(JavaPlugin plugin) {
        Logger log = plugin.getLogger();
        ConfigStore config = BootstrapConfigStore.empty();
        ModuleRegistry registry = new DefaultModuleRegistry();
        CloseableResources resources = new CloseableResources();

        wireModules(registry, config, resources, log);
        resources.addCommand(new UxmessCommand(registry, config));
        return resources;
    }

    private static void wireModules(
            ModuleRegistry registry, ConfigStore config, CloseableResources resources, Logger log) {
        for (FeatureModule module : registry.enabledModules(config)) {
            ModuleContext ctx = new ModuleContext(module.id(), config);
            if (skippedByCapability(module, ctx, log)) {
                continue;
            }
            startModule(module, ctx, resources, log);
        }
    }

    private static boolean skippedByCapability(FeatureModule module, ModuleContext ctx, Logger log) {
        LoadCondition condition = module.loadCondition();
        Optional<String> unmet = condition.unmetReason(ctx);
        if (unmet.isPresent()) {
            log.warning("module " + module.id() + " skipped — " + unmet.get());
            return true;
        }
        return false;
    }

    private static void startModule(FeatureModule module, ModuleContext ctx, CloseableResources resources, Logger log) {
        for (MigrationSet migration : module.migrations()) {
            // Gated migrations run only for an enabled, loadable module; a disabled module's tables
            // stay absent. The Flyway runner binds here when the persistence wiring lands.
            log.fine("module " + module.id() + " migration pending: " + migration.location());
        }
        long startedAt = System.nanoTime();
        module.start(ctx);
        long loadMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        resources.onClose(module::stop);
        log.info("module " + module.id() + " loaded in " + loadMillis + " ms");
    }
}
