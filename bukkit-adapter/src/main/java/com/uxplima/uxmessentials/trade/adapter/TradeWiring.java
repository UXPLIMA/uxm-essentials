package com.uxplima.uxmessentials.trade.adapter;

import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeSessions;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeView;
import com.uxplima.uxmessentials.trade.application.TradeConfig;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the trade context's adapters over the injected kernel ports. Phase 2 stands up the same-server trade
 * window: the in-memory {@link TradeSessions} registry, the {@link TradeView} that opens and drives each participant's
 * inventory view over a shared session, and the {@link TradeListener} that routes the window's click/drag/close/quit
 * events. A same-server trade holds its session purely in memory, so there is still no repository or migration to wire;
 * the economy trade port, the {@code /trade} request commands, and the cross-server bus escrow land in the later phases
 * behind this same {@link Wired} seam.
 */
@NullMarked
public final class TradeWiring {

    private TradeWiring() {}

    /** Build the trade adapters from {@code ctx}, ready to register. */
    public static Wired wire(ModuleContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        TradeConfig config = TradeConfig.from(ctx.config());
        KernelPorts kernel = ctx.kernel();
        TradeSessions sessions = new TradeSessions();
        TradeView view = new TradeView(kernel.messages(), kernel.messageSink(), kernel.scheduler(), config, sessions);
        return new Wired(List.of(), List.of(view.newListener()), config, sessions, view);
    }

    /**
     * Everything the trade module contributes once wired: the Brigadier command registrations (none until the
     * request-flow phase), the window's Bukkit listener, the resolved config, the session registry, and the view. The
     * {@link #open(Player, Player)} convenience opens a window between two online players — the seam the request-flow
     * phase and the Phase-2 tests drive, since there is no {@code /trade} command yet.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the Bukkit listeners to register
     * @param config the resolved trade config snapshot
     * @param sessions the in-memory registry of live trades
     * @param view the trade-window view that opens and drives sessions
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            TradeConfig config,
            TradeSessions sessions,
            TradeView view) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(sessions, "sessions");
            Objects.requireNonNull(view, "view");
        }

        /** Open a trade window between two online players. */
        public void open(Player a, Player b) {
            view.open(a, b);
        }

        /** Return every in-flight trade's items and close its windows; run on module stop or reload. */
        public void closeAll() {
            view.closeAll();
        }
    }
}
