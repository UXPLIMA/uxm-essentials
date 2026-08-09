package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.plugin.PluginManager;

import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Turns a published domain fact into the Bukkit event other plugins listen to.
 *
 * <p>Subscribed once to the in-process domain-event bus, so every context is bridged by this one object rather than
 * by twenty listeners. What arrives is a fact that has already happened; what leaves is a notification delivered on
 * the tick thread that owns its subject, where a listener may use the Bukkit API.
 *
 * <h2>Costing nothing when nobody is listening</h2>
 * A server with no consumer plugin still publishes every fact, on hot paths (a teleport, an economy transaction).
 * So the listener check comes first, before the mapper runs and before anything is scheduled: with no listener the
 * whole bridge is a map lookup and an array-length read, and it allocates nothing. That ordering is the one thing
 * here worth not breaking.
 */
@NullMarked
public final class BukkitEventBridge implements Consumer<DomainEvent> {

    private final EventBridgeRegistry registry;
    private final Scheduler scheduler;
    private final PluginManager plugins;
    private final Logger log;

    public BukkitEventBridge(EventBridgeRegistry registry, Scheduler scheduler, PluginManager plugins, Logger log) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.plugins = Objects.requireNonNull(plugins, "plugins");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void accept(DomainEvent fact) {
        Objects.requireNonNull(fact, "fact");
        EventBridgeRegistry.Entry<DomainEvent> entry = registry.lookup(fact);
        if (entry == null || !entry.hasListeners()) {
            return; // internal-only, or nobody is listening: build nothing, schedule nothing
        }
        UxmEvent event;
        try {
            event = entry.mapper().apply(fact);
        } catch (RuntimeException failure) {
            // A mapper reads a fact that is already committed, so a failure here loses a notification rather than
            // the change itself. Log it and let the operation stand rather than tearing down the publish.
            log.error("could not map " + fact.getClass().getSimpleName() + " to its api event", failure);
            return;
        }
        entry.region().apply(fact).schedule(scheduler, () -> plugins.callEvent(event));
    }
}
