package com.uxplima.uxmessentials.commandcontrol.adapter;

import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.CommandGateListener;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSource;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSources;
import com.uxplima.uxmessentials.commandcontrol.application.CommandControlConfig;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the command-control context's single {@code PlayerCommandPreprocessEvent} gate over the injected kernel
 * ports and the module's scoped config. The rule set is derived from config once here — per the atomic-reload rule a
 * hot-reload re-runs this wiring and swaps in a fresh listener — and the group source is chosen by probing the server
 * for LuckPerms (an empty fallback otherwise). The context persists nothing and holds no runtime state, so there is
 * nothing to drain on stop; unregistering the listener is enough.
 */
@NullMarked
public final class CommandControlWiring {

    /** The node that exempts a holder from the command gate — always allowed through, mirroring the other bypasses. */
    public static final String BYPASS_PERMISSION = "uxmessentials.commandcontrol.bypass";

    private CommandControlWiring() {}

    /** Build the command-control gate listener from {@code server} and {@code ctx}, ready to register. */
    public static Listener wire(Server server, ModuleContext ctx) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(ctx, "ctx");
        KernelPorts kernel = ctx.kernel();
        CommandControlConfig config = CommandControlConfig.from(ctx.config());
        RuleSet rules = config.toRuleSet(BYPASS_PERMISSION);
        PlayerGroupSource groups = PlayerGroupSources.create(server);
        return new CommandGateListener(rules, groups, kernel.messages(), kernel.messageSink(), config.denyMessage());
    }
}
