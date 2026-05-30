package com.uxplima.uxmessentials.bootstrap.di;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

import com.uxplima.uxmessentials.bootstrap.command.UxmessCommand;
import com.uxplima.uxmessentials.economy.adapter.EconomyWiring;
import com.uxplima.uxmessentials.homes.adapter.HomesWiring;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.LoadCondition;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.teleport.adapter.TeleportWiring;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.warps.adapter.WarpsWiring;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
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

        Persistence persistence = KernelWiring.openPersistence(plugin, config, kernelLog, registry);
        // The pool is closed last (pushed first), after every module has stopped and drained its writes.
        resources.onClose(persistence::close);

        wireModules(plugin, registry, config, kernel, persistence, resources, log);
        resources.addCommand(new UxmessCommand(registry, config));
        return resources;
    }

    private static void wireModules(
            JavaPlugin plugin,
            ModuleRegistry registry,
            ConfigStore config,
            KernelPorts kernel,
            Persistence persistence,
            CloseableResources resources,
            Logger log) {
        // teleport is wired before homes/warps (registry order is dependency-first), so its engine is
        // captured and handed to the contexts that delegate teleport execution to it.
        ContextLinks links = new ContextLinks();
        for (FeatureModule module : registry.enabledModules(config)) {
            ConfigStore moduleConfig = config.scoped(module.id().configRoot());
            ModuleContext ctx = new ModuleContext(module.id(), moduleConfig, kernel);
            if (skippedByCapability(module, ctx, log)) {
                continue;
            }
            startModule(module, ctx, resources, log);
            wireAdapters(plugin, module, ctx, persistence, resources, links);
        }
    }

    private static void wireAdapters(
            JavaPlugin plugin,
            FeatureModule module,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links) {
        // The bukkit-side adapters of each context are wired here once the context's pure module has
        // started. teleport is the first context to land and needs no database; homes builds its jOOQ
        // repository over persistence.dsl() and delegates execution to the captured teleport engine.
        if (module.id().equals(ModuleId.of("teleport"))) {
            wireTeleport(plugin, ctx, resources, links);
        } else if (module.id().equals(ModuleId.of("homes"))) {
            wireHomes(ctx, persistence, resources, links);
        } else if (module.id().equals(ModuleId.of("economy"))) {
            wireEconomy(plugin, ctx, persistence, resources, links);
        } else if (module.id().equals(ModuleId.of("warps"))) {
            wireWarps(ctx, persistence, resources, links);
        }
    }

    private static void wireTeleport(
            JavaPlugin plugin, ModuleContext ctx, CloseableResources resources, ContextLinks links) {
        TeleportWiring.Wired wired = TeleportWiring.wire(plugin, ctx);
        wired.commands().forEach(resources::addCommand);
        wired.listeners().forEach(resources::addListener);
        wired.startBackgroundWork();
        resources.onClose(wired::stop);
        links.teleportEngine = wired.services().engine();
    }

    private static void wireHomes(
            ModuleContext ctx, Persistence persistence, CloseableResources resources, ContextLinks links) {
        TeleportEngine engine = Objects.requireNonNull(
                links.teleportEngine, "homes delegates teleport execution but the teleport engine is unavailable");
        HomesWiring.Wired wired = HomesWiring.wire(ctx, persistence, engine);
        wired.commands().forEach(resources::addCommand);
    }

    private static void wireEconomy(
            JavaPlugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            CloseableResources resources,
            ContextLinks links) {
        EconomyWiring.Wired wired = EconomyWiring.wire(plugin, ctx, persistence);
        wired.commands().forEach(resources::addCommand);
        wired.start();
        resources.onClose(wired::stop);
        // Captured for warps, which lands after economy and charges a recorded per-warp cost through it.
        links.warpEconomy = wired.warpEconomy();
    }

    private static void wireWarps(
            ModuleContext ctx, Persistence persistence, CloseableResources resources, ContextLinks links) {
        TeleportEngine engine = Objects.requireNonNull(
                links.teleportEngine, "warps delegates teleport execution but the teleport engine is unavailable");
        WarpsWiring.Wired wired = WarpsWiring.wire(ctx, persistence, engine, Optional.ofNullable(links.warpEconomy));
        wired.commands().forEach(resources::addCommand);
    }

    /** Cross-context handles captured during wiring so a dependent context reaches its prerequisite. */
    private static final class ContextLinks {
        private @org.jspecify.annotations.Nullable TeleportEngine teleportEngine;
        private @org.jspecify.annotations.Nullable WarpEconomy warpEconomy;
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
        // Migrations for every enabled, loadable module were applied up front when the persistence layer
        // opened (see KernelWiring.openPersistence). A disabled module contributes no location, so its
        // tables stay absent. By the time start() runs the schema is already in place.
        long startedAt = System.nanoTime();
        module.start(ctx);
        long loadMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        resources.onClose(module::stop);
        log.info("module " + module.id() + " loaded in " + loadMillis + " ms");
    }
}
