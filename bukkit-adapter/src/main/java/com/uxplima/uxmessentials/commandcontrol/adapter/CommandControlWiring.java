package com.uxplima.uxmessentials.commandcontrol.adapter;

import java.util.List;
import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.CommandGateListener;
import com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.CommandVisibilityListener;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.CommandPermissionView;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSource;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSources;
import com.uxplima.uxmessentials.commandcontrol.application.CommandControlConfig;
import com.uxplima.uxmessentials.commandcontrol.domain.HidePolicy;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the command-control context's listeners over the injected kernel ports and the module's scoped config.
 * The rule set and the hide policy are derived from config once here — per the atomic-reload rule a hot-reload re-runs
 * this wiring and swaps in fresh listeners — and the group source is chosen by probing the server for LuckPerms (an
 * empty fallback otherwise). Two listeners are contributed: the {@link CommandGateListener} that blocks a disallowed
 * command's execution, and the {@link CommandVisibilityListener} that keeps disallowed and hidden commands out of what
 * a client sees (the sent command list, tab-completion, and the scrub-help block). The context persists nothing and
 * holds no runtime state, so there is nothing to drain on stop; unregistering the listeners is enough.
 */
@NullMarked
public final class CommandControlWiring {

    /** The node that exempts a holder from the command gate — always allowed through, mirroring the other bypasses. */
    public static final String BYPASS_PERMISSION = "uxmessentials.commandcontrol.bypass";

    /** The node that reveals the plugin-listing / help commands the plugin-hide otherwise removes and blocks. */
    public static final String VIEW_PERMISSION = "uxmessentials.commandcontrol.viewplugins";

    private CommandControlWiring() {}

    /** Build the command-control listeners from {@code server} and {@code ctx}, ready to register. */
    public static List<Listener> wire(Server server, ModuleContext ctx) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(ctx, "ctx");
        KernelPorts kernel = ctx.kernel();
        CommandControlConfig config = CommandControlConfig.from(ctx.config());
        RuleSet rules = config.toRuleSet(BYPASS_PERMISSION);
        HidePolicy hidePolicy = config.toHidePolicy(VIEW_PERMISSION);
        PlayerGroupSource groups = PlayerGroupSources.create(server);
        CommandPermissionView permissions = CommandPermissionView.backedBy(server.getCommandMap());
        Listener gate =
                new CommandGateListener(rules, groups, kernel.messages(), kernel.messageSink(), config.denyMessage());
        Listener visibility = new CommandVisibilityListener(
                rules,
                hidePolicy,
                groups,
                permissions,
                config.tabCompletionEnabled(),
                config.denyListCommands(),
                BYPASS_PERMISSION,
                kernel.messages(),
                kernel.messageSink());
        return List.of(gate, visibility);
    }
}
