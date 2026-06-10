package com.uxplima.uxmessentials.bootstrap.di;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.adapter.outbound.mapmarker.MapMarkersWiring;
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
 * pause-menu server links, the opt-in update checker, and the Dynmap/squaremap map-marker integration. All are
 * driven from the root {@code config.conf}; none owns a bounded context, so they are wired here in the bootstrap
 * surface rather than registered as a {@code FeatureModule}.
 *
 * <p>Server links apply immediately (clear-and-set the global links from {@code server-links}); an empty list
 * leaves the live links untouched. The update checker is off by default — when enabled it runs its first
 * non-blocking check on enable, optionally re-checks on its interval through the {@code Scheduler}, and contributes
 * an op-only join-notice listener the caller registers. When disabled, or when the join-notice is off, no listener
 * is produced and nothing is scheduled. The map-marker integration renders warps and spawns (homes are off by
 * default for privacy) onto whichever supported map plugin is installed; with no map plugin or {@code
 * map-markers.enabled = false} it wires nothing.
 */
@NullMarked
final class IntegrationsWiring {

    private IntegrationsWiring() {}

    /**
     * Apply server links, start the update checker, and wire the map-marker integration from {@code config},
     * returning the optional join-notice listener and the disable hooks (recurring-check stop + marker clear).
     */
    static Wired wire(JavaPlugin plugin, ConfigStore config, KernelPorts kernel, Persistence persistence) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(persistence, "persistence");
        Path dataFolder = plugin.getDataFolder().toPath();

        applyServerLinks(plugin, dataFolder, kernel);
        MapMarkersWiring.Stopped markers = wireMapMarkers(plugin, config, kernel, persistence);
        return wireUpdateChecker(plugin, config, kernel).withMapMarkers(markers.stop());
    }

    private static MapMarkersWiring.Stopped wireMapMarkers(
            JavaPlugin plugin, ConfigStore config, KernelPorts kernel, Persistence persistence) {
        // The bootstrap knows the concrete event publisher (it wired it), so the marker service can subscribe
        // to the in-process bus for live warp/home updates. KernelPorts only carries the narrow publish port.
        InProcessDomainEventPublisher events = (InProcessDomainEventPublisher) kernel.events();
        return MapMarkersWiring.wire(plugin.getServer(), config, persistence, kernel.scheduler(), events, kernel.log());
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
     * and a stop hook that runs every disable action (the update checker's recurring loop, the map-marker layer
     * clear) on disable.
     *
     * @param joinListener the update-notice listener, present only when the checker is enabled and notifies on join
     * @param stop the disable hook running every integration's teardown
     */
    record Wired(Optional<Listener> joinListener, Runnable stop) {
        Wired {
            Objects.requireNonNull(joinListener, "joinListener");
            Objects.requireNonNull(stop, "stop");
        }

        static Wired inert() {
            return new Wired(Optional.empty(), () -> {});
        }

        /** Fold the map-marker disable hook into this wiring's aggregate stop, run after the checker stop. */
        Wired withMapMarkers(Runnable markerStop) {
            Objects.requireNonNull(markerStop, "markerStop");
            Runnable both = () -> {
                stop.run();
                markerStop.run();
            };
            return new Wired(joinListener, both);
        }
    }
}
