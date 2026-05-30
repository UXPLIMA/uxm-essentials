package com.uxplima.uxmessentials.bootstrap.di;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.config.ConfigurateConfigStore;
import com.uxplima.uxmessentials.shared.adapter.outbound.cooldown.PdcCooldowns;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.adapter.outbound.log.Slf4jLogger;
import com.uxplima.uxmessentials.shared.adapter.outbound.lookup.BukkitPlayerLocator;
import com.uxplima.uxmessentials.shared.adapter.outbound.lookup.BukkitPlayerLookup;
import com.uxplima.uxmessentials.shared.adapter.outbound.lookup.BukkitWorldLookup;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.CatalogMessages;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.HoconLocaleCatalog;
import com.uxplima.uxmessentials.shared.adapter.outbound.permission.BukkitPermissions;
import com.uxplima.uxmessentials.shared.adapter.outbound.permission.LuckPermsMetaSource;
import com.uxplima.uxmessentials.shared.adapter.outbound.permission.MetaSource;
import com.uxplima.uxmessentials.shared.adapter.outbound.scheduler.FoliaScheduler;
import com.uxplima.uxmessentials.shared.adapter.outbound.sink.BukkitMessageSink;
import com.uxplima.uxmessentials.shared.adapter.outbound.warmup.SchedulerWarmups;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.LocaleCatalog;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the shared-kernel adapter implementations and bundles them into a {@link KernelPorts}.
 *
 * <p>This is the one place that news up the Bukkit/Paper outbound adapters for the shared cross-cutting
 * ports; the {@link Plugin} handle stays inside bootstrap (the adapters take the {@code Plugin}
 * interface, never {@code JavaPlugin}). LuckPerms is a soft dependency — the {@code MetaSource}
 * defaults to {@link MetaSource#none()} and only binds to the LuckPerms-backed source when LuckPerms is
 * installed, so the {@code net.luckperms} symbols are never loaded on a server without it.
 */
@NullMarked
final class KernelWiring {

    /** The shared chat prefix catalog key, injected into the sink's {@code <prefix>} tag. */
    private static final MessageKey PREFIX_KEY = () -> "prefix";

    private KernelWiring() {}

    /** The HOCON config file backing the {@link ConfigStore}; created from the plugin data folder. */
    static ConfigStore loadConfig(Plugin plugin, Logger log) {
        Path file = plugin.getDataFolder().toPath().resolve("config.conf");
        return ConfigurateConfigStore.load(file, log);
    }

    /** A {@link Logger} over the plugin's SLF4J logger. */
    static Logger logger(Plugin plugin) {
        return new Slf4jLogger(plugin.getSLF4JLogger());
    }

    /** Build every shared outbound port and bundle them for module injection. */
    static KernelPorts wire(Plugin plugin, ConfigStore config, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(log, "log");

        Scheduler scheduler = new FoliaScheduler(plugin);
        Permissions permissions = new BukkitPermissions(metaSource(log));
        LocaleCatalog catalog = new HoconLocaleCatalog(log);
        String prefix = catalog.template(java.util.Locale.ENGLISH, PREFIX_KEY);

        return new KernelPorts(
                scheduler,
                permissions,
                new PdcCooldowns(plugin, permissions, Clock.systemUTC()),
                new SchedulerWarmups(scheduler, permissions),
                new CatalogMessages(catalog),
                new BukkitMessageSink(scheduler, prefix),
                new BukkitPlayerLookup(),
                new BukkitWorldLookup(),
                new BukkitPlayerLocator(),
                new InProcessDomainEventPublisher(log),
                log);
    }

    private static MetaSource metaSource(Logger log) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return MetaSource.none();
        }
        // Loading LuckPermsMetaSource (and thus the net.luckperms symbols) only happens past the
        // plugin-present guard, so a server without LuckPerms never resolves those classes.
        LuckPerms luckPerms = LuckPermsProvider.get();
        return new LuckPermsMetaSource(luckPerms, log);
    }
}
