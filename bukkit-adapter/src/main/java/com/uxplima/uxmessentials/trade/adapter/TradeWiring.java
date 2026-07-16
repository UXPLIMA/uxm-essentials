package com.uxplima.uxmessentials.trade.adapter;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.outbound.log.Slf4jLogger;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.trade.adapter.inbound.command.TradeCommand;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeMoneyPrompt;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeSessions;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeView;
import com.uxplima.uxmessentials.trade.adapter.outbound.LoggingTradeAudit;
import com.uxplima.uxmessentials.trade.application.TradeConfig;
import com.uxplima.uxmessentials.trade.application.TradeCooldown;
import com.uxplima.uxmessentials.trade.application.TradeMessageKey;
import com.uxplima.uxmessentials.trade.application.TradeRequests;
import com.uxplima.uxmessentials.trade.application.TradeSettlement;
import com.uxplima.uxmessentials.trade.application.port.TradeAudit;
import com.uxplima.uxmessentials.trade.application.port.TradeEconomy;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/**
 * Constructs the trade context's adapters over the injected kernel ports. The same-server trade window is the in-memory
 * {@link TradeSessions} registry, the {@link TradeView} that opens and drives each participant's inventory view over a
 * shared session, and the {@link TradeListener} that routes the window's click/drag/close/quit events. Phase 3 adds the
 * money row: the {@link TextInput}-backed amount prompt behind the {@link TradeMoneyPrompt} seam and the
 * {@link TradeSettlement} that moves staked money all-or-nothing through the {@link TradeEconomy} port at commit — the
 * port is present only when economy is wired, so without it a trade moves items only. Phase 4 adds the {@code /trade}
 * request/accept flow — the in-memory {@link TradeRequests} book and the per-player {@link TradeCooldown}, both clock
 * driven — plus the config-gated completed-trade audit through the {@link TradeAudit} port. A same-server trade holds
 * its session purely in memory, so there is still no repository or migration; the cross-server bus escrow lands in the
 * final phase behind this same {@link Wired} seam.
 */
@NullMarked
public final class TradeWiring {

    /** The dedicated audit channel a completed trade's line is written to — the same channel the other contexts use. */
    private static final String AUDIT_CHANNEL = "com.uxplima.uxmessentials.audit";

    private TradeWiring() {}

    /**
     * Build the trade adapters from {@code ctx}, ready to register. The {@code economy} seam is present only when the
     * economy provider is wired; when absent the money row is hidden and a trade moves items only.
     */
    public static Wired wire(ModuleContext ctx, TextInput textInput, @Nullable TradeEconomy economy) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(textInput, "textInput");
        TradeConfig config = TradeConfig.from(ctx.config());
        KernelPorts kernel = ctx.kernel();
        TradeSessions sessions = new TradeSessions();
        @Nullable TradeSettlement settlement = economy != null ? new TradeSettlement(economy) : null;
        TradeMoneyPrompt moneyPrompt = (player, viewer, currencyId, onSubmit, onCancel) -> textInput.prompt(
                player,
                viewer,
                InputRequest.of("trade.money", TradeMessageKey.TRADE_MONEY_PROMPT, Map.of("currency", currencyId)),
                onSubmit,
                onCancel);
        TradeAudit audit = new LoggingTradeAudit(new Slf4jLogger(LoggerFactory.getLogger(AUDIT_CHANNEL)));
        TradeView view = new TradeView(
                kernel.messages(),
                kernel.messageSink(),
                kernel.scheduler(),
                config,
                sessions,
                moneyPrompt,
                settlement,
                audit);
        // The request/accept flow: a per-request expiry book and a per-player cooldown, both driven by the same clock,
        // gate /trade and open the window through the view once the target accepts. The clock is UTC so a request's
        // lifetime is independent of the host's zone (mirrors the moderation context).
        Clock clock = Clock.systemUTC();
        TradeRequests requests = new TradeRequests(clock, Duration.ofSeconds(config.requestExpirySeconds()));
        TradeCooldown cooldown = new TradeCooldown(clock, Duration.ofSeconds(config.cooldownSeconds()));
        TradeCommand command = new TradeCommand(requests, cooldown, config, sessions, view::open, kernel.messages());
        return new Wired(List.of(command), List.of(view.newListener()), config, sessions, view);
    }

    /**
     * Everything the trade module contributes once wired: the Brigadier {@code /trade} command, the window's Bukkit
     * listener, the resolved config, the session registry, and the view. The {@link #open(Player, Player)} convenience
     * opens a window between two online players — the seam {@code /trade accept} and the window tests drive.
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
