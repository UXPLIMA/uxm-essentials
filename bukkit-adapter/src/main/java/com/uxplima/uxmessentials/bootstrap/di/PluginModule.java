package com.uxplima.uxmessentials.bootstrap.di;

import java.util.Optional;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

import com.uxplima.uxmessentials.bootstrap.command.UxmessCommand;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.LoadCondition;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.teleport.adapter.TeleportWiring;
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
        com.uxplima.uxmessentials.shared.application.port.Logger kernelLog = KernelWiring.logger(plugin);
        ConfigStore config = KernelWiring.loadConfig(plugin, kernelLog);
        KernelPorts kernel = KernelWiring.wire(plugin, config, kernelLog);
        ModuleRegistry registry = new DefaultModuleRegistry();
        CloseableResources resources = new CloseableResources();

        wireModules(plugin, registry, config, kernel, resources, log);
        resources.addCommand(new UxmessCommand(registry, config));
        return resources;
    }

    private static void wireModules(
            JavaPlugin plugin,
            ModuleRegistry registry,
            ConfigStore config,
            KernelPorts kernel,
            CloseableResources resources,
            Logger log) {
        for (FeatureModule module : registry.enabledModules(config)) {
            ConfigStore moduleConfig = config.scoped(module.id().configRoot());
            ModuleContext ctx = new ModuleContext(module.id(), moduleConfig, kernel);
            if (skippedByCapability(module, ctx, log)) {
                continue;
            }
            startModule(module, ctx, resources, log);
            wireAdapters(plugin, module, ctx, resources);
        }
    }

    private static void wireAdapters(
            JavaPlugin plugin, FeatureModule module, ModuleContext ctx, CloseableResources resources) {
        // The bukkit-side adapters of each context are wired here once the context's pure module has
        // started. teleport is the first context to land; the others plug in at their own id below.
        if (module.id().equals(ModuleId.of("teleport"))) {
            TeleportWiring.Wired wired = TeleportWiring.wire(plugin, ctx);
            wired.commands().forEach(resources::addCommand);
            wired.listeners().forEach(resources::addListener);
            wired.startBackgroundWork();
            resources.onClose(wired::stop);
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
