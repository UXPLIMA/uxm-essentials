package com.uxplima.uxmessentials.bootstrap.di;

import java.nio.file.Path;
import java.util.Objects;

import org.bukkit.plugin.java.JavaPlugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.serverlinks.ServerLinksApplier;
import com.uxplima.uxmessentials.shared.adapter.outbound.serverlinks.ServerLinksConfig;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import org.jspecify.annotations.NullMarked;

/**
 * Wires the cross-cutting server-integration polish features that belong to no single feature context. Today that
 * is the 1.21+ pause-menu server links, driven from the root {@code config.conf}: it owns no database table, no
 * command, and no bounded context, so it is wired here in the bootstrap surface rather than registered as a
 * {@code FeatureModule}.
 *
 * <p>Server links apply immediately on enable (clear-and-set the global links from the {@code server-links}
 * block); an empty list leaves the live links untouched so links pushed by other plugins or the vanilla server
 * survive.
 */
@NullMarked
final class IntegrationsWiring {

    private IntegrationsWiring() {}

    /** Apply server links from {@code plugin}'s root {@code config.conf} through {@code kernel}'s ports. */
    static void wire(JavaPlugin plugin, KernelPorts kernel) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(kernel, "kernel");
        Path dataFolder = plugin.getDataFolder().toPath();
        ServerLinksApplier applier = new ServerLinksApplier(plugin.getServer(), kernel.scheduler(), kernel.log());
        applier.apply(new ServerLinksConfig(dataFolder, kernel.log()).read());
    }
}
