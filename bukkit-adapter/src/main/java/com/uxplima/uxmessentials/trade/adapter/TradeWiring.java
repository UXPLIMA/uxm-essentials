package com.uxplima.uxmessentials.trade.adapter;

import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.trade.application.TradeConfig;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the trade context's adapters and use cases over the injected kernel ports. Phase 1 resolves only the
 * typed {@link TradeConfig} from the module's scoped config; the trade window ({@code /trade}), its inventory
 * listeners, the economy trade port, and the cross-server bus transport are wired here as their behaviour phases land,
 * returning through the same {@link Wired} record so the caller registers them uniformly.
 *
 * <p>A same-server trade holds its {@code TradeSession} purely in memory, so there is no repository or migration to
 * wire in this phase; the cross-server escrow store lands with the cross-server phase.
 */
@NullMarked
public final class TradeWiring {

    private TradeWiring() {}

    /** Build the trade adapters and use cases from {@code ctx}, ready to register. */
    public static Wired wire(ModuleContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        TradeConfig config = TradeConfig.from(ctx.config());
        // Phase 1 stands up the module and its config only; the /trade commands and the window listeners land with the
        // inbound adapter in the behaviour phases, adding to these lists behind this same seam.
        return new Wired(List.of(), List.of(), config);
    }

    /**
     * Everything the trade module contributes once wired: the Brigadier command registrations, the Bukkit listeners,
     * and the resolved config the later phases read. Phase 1 contributes no command and no listener — the record's
     * shape is what the behaviour phases fill in — so the caller registers nothing yet.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the Bukkit listeners to register
     * @param config the resolved trade config snapshot
     */
    public record Wired(List<CommandRegistration> commands, List<Listener> listeners, TradeConfig config) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(config, "config");
        }
    }
}
