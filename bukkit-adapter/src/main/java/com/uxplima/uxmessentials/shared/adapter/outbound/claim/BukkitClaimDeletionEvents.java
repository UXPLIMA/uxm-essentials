package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import com.uxplima.uxmessentials.shared.application.port.ClaimDeletionEvents;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimRegion;
import org.jspecify.annotations.NullMarked;

/**
 * The Bukkit-side {@link ClaimDeletionEvents}: it holds the registered sinks and owns the typed bridges that
 * feed them. {@link #register(Plugin, Server)} installs one Bukkit listener per claim plugin that is present,
 * and each bridge fans a removed claim's {@link ClaimRegion} out to every sink through {@link #emit}.
 *
 * <p>Registration is guarded on the plugin manager: a bridge whose handler parameter is a claim-plugin event
 * class is registered only when that plugin is installed. Registering a listener whose event type is absent
 * can raise {@link NoClassDefFoundError} on some server versions while the handler is being resolved, so the
 * present-guard is a correctness requirement, not an optimisation. Both Lands and GriefPrevention declare
 * {@code load: BEFORE} against this plugin, so when either is installed it is already loaded at the moment
 * {@code register} runs.
 *
 * <p>With both plugins installed, both bridges are registered and a sink hears from each; that is correct,
 * since a given claim belongs to exactly one plugin, so no de-duplication is needed. With neither installed,
 * {@code register} adds no listener and returns without throwing.
 *
 * <p>Sinks live in a {@link CopyOnWriteArrayList} so a sink registered during wiring is safely visible to a
 * bridge emitting from the server thread without locking on the emit path.
 */
@NullMarked
public final class BukkitClaimDeletionEvents implements ClaimDeletionEvents {

    private static final String LANDS = "Lands";
    private static final String GRIEF_PREVENTION = "GriefPrevention";

    private final Logger log;
    private final List<Consumer<ClaimRegion>> sinks = new CopyOnWriteArrayList<>();

    public BukkitClaimDeletionEvents(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void onDeleted(Consumer<ClaimRegion> sink) {
        sinks.add(Objects.requireNonNull(sink, "sink"));
    }

    /**
     * Installs a Bukkit listener for each claim plugin present, so that plugin's removals reach the registered
     * sinks. Skips any plugin that is absent — no listener, no throw. Safe to call once on enable.
     *
     * @param plugin the owning plugin the listeners are registered under
     * @param server the server whose plugin manager is consulted for presence and used to register
     */
    public void register(Plugin plugin, Server server) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(server, "server");
        PluginManager pluginManager = server.getPluginManager();
        if (pluginManager.getPlugin(GRIEF_PREVENTION) != null) {
            pluginManager.registerEvents(new GriefPreventionClaimDeletion(this::emit, log), plugin);
        }
        if (pluginManager.getPlugin(LANDS) != null) {
            pluginManager.registerEvents(new LandsClaimDeletion(this::emit, log), plugin);
        }
    }

    // Package-private, not private: it is the shared emit path each bridge is handed as a Consumer, and the
    // fan-out-to-every-sink behaviour is exercised here directly in the same-package test.
    void emit(ClaimRegion region) {
        for (Consumer<ClaimRegion> sink : sinks) {
            sink.accept(region);
        }
    }
}
