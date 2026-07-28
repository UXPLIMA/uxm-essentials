package com.uxplima.uxmessentials.bootstrap;

import java.util.logging.Level;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import com.uxplima.uxmessentials.bootstrap.di.CloseableResources;
import com.uxplima.uxmessentials.bootstrap.di.PluginModule;
import com.uxplima.uxmessentials.poses.adapter.outbound.WorldGuardPoseFlagRegistrar;
import com.uxplima.uxmessentials.shared.adapter.outbound.worldguard.WorldGuardSetPwarpFlagRegistrar;
import com.uxplima.uxmessentials.worlds.adapter.outbound.WorldGeneratorResolver;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The thin {@link JavaPlugin} shell: wires the DI graph on enable, closes it on disable.
 *
 * <p>The plugin instance is never exposed via a static accessor — services are constructed and
 * injected by {@link PluginModule}, which is the only holder of this reference. The {@code COMMANDS}
 * lifecycle handler publishes the already-module-filtered command set built during wiring, so a
 * disabled module's command literal never reaches the dispatcher.
 */
@NullMarked
public final class UxmEssentialsPlugin extends JavaPlugin {

    private @Nullable CloseableResources resources;

    /**
     * Registers our WorldGuard custom flags before any plugin is enabled. WorldGuard locks its flag registry the moment
     * it enables, so registration has to happen in the load phase — and a Paper
     * {@link io.papermc.paper.plugin.bootstrap.PluginBootstrap} runs even earlier, before WorldGuard has created that
     * registry, so {@code onLoad} is the correct hook. Each call is a silent no-op when WorldGuard is absent (it never
     * loads a WorldGuard class on a server without it), so both are safe to run unconditionally here, before the module
     * registry that decides whether a feature is enabled even exists.
     */
    @Override
    public void onLoad() {
        WorldGuardPoseFlagRegistrar.register(getServer(), getLogger());
        WorldGuardSetPwarpFlagRegistrar.register(getServer(), getLogger());
    }

    @Override
    public void onEnable() {
        getLogger().info("╔══════════════════════════════════════════════════════════╗");
        getLogger().info("║                                                          ║");
        getLogger().info("║   ██╗   ██╗██╗  ██╗███╗   ███╗                           ║");
        getLogger().info("║   ██║   ██║╚██╗██╔╝████╗ ████║                           ║");
        getLogger().info("║   ██║   ██║ ╚███╔╝ ██╔████╔██║                           ║");
        getLogger().info("║   ██║   ██║ ██╔██╗ ██║╚██╔╝██║                           ║");
        getLogger().info("║   ╚██████╔╝██╔╝ ██╗██║ ╚═╝ ██║                           ║");
        getLogger().info("║    ╚═════╝ ╚═╝  ╚═╝╚═╝     ╚═╝                           ║");
        getLogger().info("║                                                          ║");
        getLogger().info("║   Essentials v" + getPluginMeta().getVersion() + "                                      ║");
        getLogger().info("║   Author: uxplima                                        ║");
        getLogger().info("║                                                          ║");
        getLogger().info("╚══════════════════════════════════════════════════════════╝");

        long startTime = System.currentTimeMillis();

        getLogger().info("[1/4] Writing default resources...");
        // First-run side effect: drop the editable default config files next to the database so an operator has
        // something to configure. Existing files are never overwritten; an update only appends the settings it
        // added, so a new knob is visible in their file instead of only in the jar (see DefaultResources).
        DefaultResources.writeInto(
                getDataFolder().toPath(), getLogger(), getPluginMeta().getVersion());

        getLogger().info("[2/4] Wiring core modules and persistence...");
        CloseableResources wired = PluginModule.wire(this);
        this.resources = wired;

        getLogger().info("[3/4] Registering command handlers...");
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            // Guard each publish so one malformed command (a broken build() or a duplicate literal) is logged
            // and skipped instead of aborting the whole batch and leaving every later command unregistered.
            wired.commands().forEach(command -> {
                try {
                    registrar.register(command.build(), command.description(), command.aliases());
                } catch (RuntimeException failure) {
                    getLogger()
                            .log(
                                    Level.SEVERE,
                                    "failed to register command "
                                            + command.getClass().getSimpleName() + " — skipped",
                                    failure);
                }
            });
        });

        getLogger().info("[4/4] Registering listener hooks...");
        // Same isolation on the listener side: one listener that throws on registration must not stop the rest.
        wired.listeners().forEach(listener -> {
            try {
                getServer().getPluginManager().registerEvents(listener, this);
            } catch (RuntimeException failure) {
                getLogger()
                        .log(
                                Level.SEVERE,
                                "failed to register listener "
                                        + listener.getClass().getSimpleName() + " — skipped",
                                failure);
            }
        });

        // Initialize bStats Metrics
        int pluginId = 31811;
        new org.bstats.bukkit.Metrics(this, pluginId);

        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info("╔══════════════════════════════════════════════════════════╗");
        getLogger().info("║  UxmEssentials enabled successfully in " + loadTime + "ms!          ║");
        getLogger().info("╚══════════════════════════════════════════════════════════╝");
    }

    /**
     * Serves our built-in {@code uxmEssentials:void|flat} generators to worlds whose
     * {@code generator:} is configured in server.properties or requested by another plugin. Reads the
     * resolver the worlds module captured during wiring; null (worlds disabled or an unknown id) falls
     * back to vanilla generation.
     */
    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(String worldName, @Nullable String id) {
        CloseableResources wired = this.resources;
        return resolveGenerator(wired == null ? null : wired.worldGeneratorResolver(), id);
    }

    /** The pure resolve step behind {@link #getDefaultWorldGenerator}, factored out for unit testing. */
    static @Nullable ChunkGenerator resolveGenerator(@Nullable WorldGeneratorResolver resolver, @Nullable String id) {
        return resolver == null || id == null ? null : resolver.resolve(id).orElse(null);
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling UxmEssentials...");
        CloseableResources wired = this.resources;
        if (wired != null) {
            getLogger().info("Closing active modules and dependencies...");
            wired.close(); // stops every started module in reverse wiring order
            this.resources = null;
        }
        getLogger().info("UxmEssentials has been disabled!");
    }
}
