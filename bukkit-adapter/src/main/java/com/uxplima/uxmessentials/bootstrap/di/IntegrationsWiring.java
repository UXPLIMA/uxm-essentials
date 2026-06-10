package com.uxplima.uxmessentials.bootstrap.di;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.serverlinks.ServerLinksApplier;
import com.uxplima.uxmessentials.shared.adapter.outbound.serverlinks.ServerLinksConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.update.UpdateCheckSettings;
import com.uxplima.uxmessentials.shared.adapter.outbound.update.UpdateChecker;
import com.uxplima.uxmessentials.shared.adapter.outbound.update.UpdateNoticeListener;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.domain.Version;
import org.jspecify.annotations.NullMarked;

/**
 * Wires the cross-cutting server-integration polish features that belong to no single feature context: the 1.21+
 * pause-menu server links and the opt-in update checker. Both are driven from the root {@code config.conf}; neither
 * owns a database table, a command, or a bounded context, so they are wired here in the bootstrap surface rather
 * than registered as a {@code FeatureModule}.
 *
 * <p>Server links apply immediately (clear-and-set the global links from {@code server-links}); an empty list
 * leaves the live links untouched. The update checker is off by default — when enabled it runs its first
 * non-blocking check on enable, optionally re-checks on its interval through the {@code Scheduler}, and contributes
 * an op-only join-notice listener the caller registers. When disabled, or when the join-notice is off, no listener
 * is produced and nothing is scheduled.
 */
@NullMarked
final class IntegrationsWiring {

    private IntegrationsWiring() {}

    /**
     * Apply server links and start the update checker from {@code config}, returning the optional join-notice
     * listener and a stop hook for the recurring check.
     */
    static Wired wire(JavaPlugin plugin, ConfigStore config, KernelPorts kernel) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(kernel, "kernel");
        Path dataFolder = plugin.getDataFolder().toPath();

        applyServerLinks(plugin, dataFolder, kernel);
        return wireUpdateChecker(plugin, config, kernel);
    }

    private static void applyServerLinks(JavaPlugin plugin, Path dataFolder, KernelPorts kernel) {
        ServerLinksApplier applier = new ServerLinksApplier(plugin.getServer(), kernel.scheduler(), kernel.log());
        applier.apply(new ServerLinksConfig(dataFolder, kernel.log()).read());
    }

    private static Wired wireUpdateChecker(JavaPlugin plugin, ConfigStore config, KernelPorts kernel) {
        UpdateCheckSettings settings = UpdateCheckSettings.from(config);
        if (!settings.enabled()) {
            return Wired.inert();
        }
        Version current = Version.parse(plugin.getPluginMeta().getVersion()).orElse(new Version(0, 0, 0));
        UpdateChecker checker = new UpdateChecker(kernel.scheduler(), kernel.log(), current, settings);
        checker.start();
        Optional<Listener> listener = settings.notifyOpsOnJoin()
                ? Optional.of(new UpdateNoticeListener(
                        checker, settings.sourceUrl(), kernel.messages(), kernel.messageSink()))
                : Optional.empty();
        return new Wired(listener, checker::stop);
    }

    /**
     * What the integrations wiring contributes: an optional op-join update-notice listener the caller registers,
     * and a stop hook that halts the update checker's recurring loop on disable.
     *
     * @param joinListener the update-notice listener, present only when the checker is enabled and notifies on join
     * @param stop the disable hook stopping the recurring check
     */
    record Wired(Optional<Listener> joinListener, Runnable stop) {
        Wired {
            Objects.requireNonNull(joinListener, "joinListener");
            Objects.requireNonNull(stop, "stop");
        }

        static Wired inert() {
            return new Wired(Optional.empty(), () -> {});
        }
    }
}
