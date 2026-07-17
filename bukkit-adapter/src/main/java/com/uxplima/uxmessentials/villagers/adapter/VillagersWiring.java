package com.uxplima.uxmessentials.villagers.adapter;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.villagers.adapter.inbound.command.VillagerCommand;
import com.uxplima.uxmessentials.villagers.adapter.inbound.gui.VillagerManagerListener;
import com.uxplima.uxmessentials.villagers.adapter.inbound.gui.VillagerManagerView;
import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.ClickToTradeListener;
import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.DisableTradesListener;
import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.VillagerRecipeReapplyListener;
import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.VillagerTradeListener;
import com.uxplima.uxmessentials.villagers.adapter.outbound.PdcVillagerFlags;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerRecipeStore;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerRestockSweep;
import com.uxplima.uxmessentials.villagers.application.VillagersConfig;
import com.uxplima.uxmessentials.villagers.domain.RestockPolicy;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Constructs the villagers context's adapters over the injected kernel ports and produces the listeners, commands, and
 * restock sweep the plugin registers. Each feature wires only when its config switch is on: the
 * {@link VillagerTradeListener} lands only when infinite trading or instant restock is enabled; the
 * {@link VillagerRestockSweep} schedules a task only when the restock timer is enabled; the trade manager (the
 * {@code /villager manager} command, its GUI listener, and the load-time recipe reapply) wires only when
 * {@code trade-manager} is on; and the {@link ClickToTradeListener} lands only when {@code click-to-trade} is on. The
 * {@link DisableTradesListener} always registers when the module is on, because it also honours the per-villager
 * disable flag the manager sets — with the global switch off and no flag it is an inert no-op.
 *
 * <p>The context persists nothing relational — the last-restock stamp, the disable flag, and the manager's custom
 * recipe set are all PDC state on the villager entity — so there is no repository or migration. The sweep's repeating
 * task is started here and any still-open manager window is drained on {@link Wired#stop()}, so a disable or reload
 * leaves no scheduled work and no unsaved edit behind.
 */
@NullMarked
public final class VillagersWiring {

    /** The permission gating the {@code /villager manager} staff tool. */
    private static final String MANAGER_PERMISSION = "uxmessentials.villagers.manager";
    /** The permission gating click-to-trade access. */
    private static final String TRADE_PERMISSION = "uxmessentials.villagers.trade";

    private VillagersWiring() {}

    /**
     * Build the villagers listeners and commands and start the restock sweep from {@code ctx}, ready to register.
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
        VillagerRecipeStore recipeStore = new VillagerRecipeStore();

        List<Listener> listeners = new ArrayList<>();
        List<CommandRegistration> commands = new ArrayList<>();
        // Infinite trading and instant restock both react to a completed trade, so they share one listener; it lands
        // only when at least one of the two is on, and each flag selects its own reset shape inside the handler.
        if (config.infiniteTrading().enabled() || config.instantRestock().enabled()) {
            listeners.add(new VillagerTradeListener(
                    config.infiniteTrading().enabled(), config.instantRestock().enabled()));
        }
        // The disable listener honours both the global switch and the per-villager PDC flag, so it registers for the
        // whole enabled module; with the switch off and no flag set it cancels nothing.
        listeners.add(new DisableTradesListener(config.disableTrades().enabled(), flags, kernel.messages()));

        VillagerManagerView managerView = null;
        if (config.tradeManager().enabled()) {
            managerView =
                    new VillagerManagerView(new GuiText(kernel.messages()), kernel.scheduler(), flags, recipeStore);
            listeners.add(new VillagerManagerListener(managerView));
            listeners.add(new VillagerRecipeReapplyListener(recipeStore));
            commands.add(new VillagerCommand(MANAGER_PERMISSION, managerView, kernel.messages()));
        }
        if (config.clickToTrade().enabled()) {
            listeners.add(new ClickToTradeListener(
                    config.disableTrades().enabled(), TRADE_PERMISSION, flags, VillagersWiring::openTrade));
        }

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
        VillagerManagerView viewToDrain = managerView;
        Runnable stop = () -> stop(sweepTask, viewToDrain, kernel.log());
        return new Wired(listeners, commands, stop);
    }

    // The click-to-trade opener: force-open the villager's trade window. Both openMerchant overloads carry a
    // deprecation for their InventoryView return type, but it is the only API that opens a merchant window, and the
    // return value is unused here.
    @SuppressWarnings("deprecation") // openMerchant is the only API to force-open a villager's trade window
    private static void openTrade(org.bukkit.entity.Player player, org.bukkit.entity.Villager villager) {
        player.openMerchant((org.bukkit.inventory.Merchant) villager, true);
    }

    // Cancel the repeating sweep and drain any still-open manager window on module stop, logging any failure with
    // context rather than swallowing it, so a disable or reload strands no scheduled task and loses no edit.
    private static void stop(AutoCloseable sweepTask, @Nullable VillagerManagerView managerView, Logger log) {
        if (managerView != null) {
            managerView.flushAll();
        }
        try {
            sweepTask.close();
        } catch (Exception failure) {
            log.error("failed to cancel the villager restock sweep", failure);
        }
    }

    /**
     * Everything the villagers module contributes once wired: the listeners and commands to register and the teardown
     * hook that cancels the restock sweep and saves any open manager window.
     *
     * @param listeners the Bukkit listeners to register
     * @param commands the Brigadier commands to register
     * @param stop the teardown hook run on module stop
     */
    public record Wired(List<Listener> listeners, List<CommandRegistration> commands, Runnable stop) {

        public Wired {
            listeners = List.copyOf(listeners);
            commands = List.copyOf(commands);
            Objects.requireNonNull(stop, "stop");
        }
    }
}
