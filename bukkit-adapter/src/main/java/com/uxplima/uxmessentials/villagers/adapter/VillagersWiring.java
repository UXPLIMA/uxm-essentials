package com.uxplima.uxmessentials.villagers.adapter;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.DisableTradesListener;
import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.VillagerTradeListener;
import com.uxplima.uxmessentials.villagers.adapter.outbound.PdcVillagerFlags;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerRestockSweep;
import com.uxplima.uxmessentials.villagers.application.VillagersConfig;
import com.uxplima.uxmessentials.villagers.domain.RestockPolicy;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the villagers context's adapters over the injected kernel ports and produces the listeners and the
 * restock sweep the plugin registers. Each trade-availability feature wires only when its config switch is on: the
 * {@link VillagerTradeListener} lands only when infinite trading or instant restock is enabled, and the
 * {@link VillagerRestockSweep} schedules a task only when the restock timer is enabled. The
 * {@link DisableTradesListener} always registers when the module is on, because it also honours the per-villager
 * disable flag the Phase-2 manager sets — with the global switch off and no flag it is an inert no-op.
 *
 * <p>The context persists nothing — the last-restock stamp and the disable flag are PDC state on the villager entity —
 * so there is no repository or migration. The sweep's repeating task is started here and its cancel wrapped into the
 * {@link Wired#stop()} the caller registers, so a disable or reload leaves no scheduled work behind.
 */
@NullMarked
public final class VillagersWiring {

    private VillagersWiring() {}

    /**
     * Build the villagers listeners and start the restock sweep from {@code ctx}, ready to register.
     *
     * @param ctx the module context carrying the scoped config and kernel ports
     * @param server the server the restock sweep enumerates villagers across on the global region thread
     */
    public static Wired wire(ModuleContext ctx, Server server) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(server, "server");
        KernelPorts kernel = ctx.kernel();
        VillagersConfig config = VillagersConfig.from(ctx.config());
        PdcVillagerFlags flags = new PdcVillagerFlags();

        List<Listener> listeners = new ArrayList<>();
        // Infinite trading and instant restock both react to a completed trade, so they share one listener; it lands
        // only when at least one of the two is on, and each flag selects its own reset shape inside the handler.
        if (config.infiniteTrading().enabled() || config.instantRestock().enabled()) {
            listeners.add(new VillagerTradeListener(
                    config.infiniteTrading().enabled(), config.instantRestock().enabled()));
        }
        // The disable listener honours both the global switch and the per-villager PDC flag, so it registers for the
        // whole enabled module; with the switch off and no flag set it cancels nothing.
        listeners.add(new DisableTradesListener(config.disableTrades().enabled(), flags, kernel.messages()));

        RestockPolicy policy = new RestockPolicy(config.restock().interval());
        VillagerRestockSweep sweep = new VillagerRestockSweep(
                server,
                kernel.scheduler(),
                flags,
                policy,
                config.restock().interval(),
                Clock.systemUTC(),
                config.restock().enabled());
        AutoCloseable sweepTask = sweep.start();
        Runnable stop = () -> cancelSweep(sweepTask, kernel.log());
        return new Wired(listeners, stop);
    }

    // Cancel the repeating sweep on module stop, logging any failure with context rather than swallowing it, so a
    // disable or reload strands no scheduled task.
    private static void cancelSweep(AutoCloseable sweepTask, Logger log) {
        try {
            sweepTask.close();
        } catch (Exception failure) {
            log.error("failed to cancel the villager restock sweep", failure);
        }
    }

    /**
     * Everything the villagers module contributes once wired: the trade / interact listeners to register and the
     * teardown hook that cancels the restock sweep.
     *
     * @param listeners the Bukkit listeners to register
     * @param stop the teardown hook that cancels the restock sweep on module stop
     */
    public record Wired(List<Listener> listeners, Runnable stop) {

        public Wired {
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(stop, "stop");
        }
    }
}
