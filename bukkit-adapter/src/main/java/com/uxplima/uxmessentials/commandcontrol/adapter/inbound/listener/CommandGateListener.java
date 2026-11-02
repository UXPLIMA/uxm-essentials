package com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.uxplima.uxmessentials.commandcontrol.adapter.CommandControlState;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.BukkitPlayerFacts;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSource;
import com.uxplima.uxmessentials.commandcontrol.domain.PlayerFacts;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The command whitelist / blacklist gate: on {@link PlayerCommandPreprocessEvent} it reads the command with its
 * arguments, so an entry such as {@code "gamemode creative"} decides that one argument, resolves the rule set for the
 * player's current world
 * (a per-world override when one is configured, else the base rule set), consults it with the player's facts (their
 * group via the {@link PlayerGroupSource}, permission checks via Bukkit), and on {@link RuleSet.Decision#DENY}
 * cancels the dispatch and sends the configured deny line, the vanilla-style "unknown command" so a hidden command
 * reads as nonexistent, or the "no permission" line, per config.
 *
 * <p>The gate also closes the namespace-bypass hole: a non-bypass player who prefixes a denied command with its
 * namespace ({@code /minecraft:gamemode}, {@code /plugin:cmd}) reaches the same handler as the bare command yet reads
 * as a different root, so the {@link RuleSet} alone would let it through. When {@code block-namespace-bypass} is on the
 * rule set strips the prefix and asks about the bare form too, so {@code /minecraft:gamemode} is blocked exactly when {@code /gamemode} is. The plugin-hide half of the escape
 * is handled in the visibility listener, whose {@code HidePolicy} already folds the {@code namespace:} prefix.
 *
 * <p>Runs at {@link EventPriority#HIGH} so cooperating plugins at NORMAL still see an uncancelled event but the
 * dispatch is stopped before the vanilla/dispatcher handler. The console is never gated. This listens only to the
 * player-command event, and a {@code .bypass} holder is always allowed (the {@code RuleSet} short-circuits on the
 * bypass node before any group lookup). When the world's rule set is inert (a blacklist with empty lists) the listener
 * short-circuits before any per-command work, so an operator who leaves the lists blank pays nothing.
 *
 * <p>The gate is client-agnostic: {@link PlayerCommandPreprocessEvent} fires for a Bedrock / Geyser player (routed
 * through Floodgate as an ordinary {@code Player}) exactly as for a Java client, so the filter needs no client probe
 * and no Geyser soft-dependency. It also runs per backend: a command a proxy forwards to this server is gated on
 * arrival here, so cross-server setups gate on each backend rather than at the proxy.
 */
@NullMarked
public final class CommandGateListener implements Listener {

    private final CommandControlState state;
    private final PlayerGroupSource groups;
    private final Messages messages;
    private final MessageSink sink;

    public CommandGateListener(
            CommandControlState state, PlayerGroupSource groups, Messages messages, MessageSink sink) {
        this.state = Objects.requireNonNull(state, "state");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        CommandControlState.Snapshot rulesInForce = state.current();
        RuleSet rules = rulesInForce.worldRules().forWorld(worldName(player));
        if (rules.isInert()) {
            return;
        }
        String command = event.getMessage();
        if (command.isBlank() || command.equals("/")) {
            return;
        }
        PlayerFacts facts = new BukkitPlayerFacts(player, groups);
        if (rules.explain(command, facts, rulesInForce.blockNamespaceBypass()).allowed()) {
            return;
        }
        event.setCancelled(true);
        PlayerRef who = BukkitRefs.toRef(player);
        sink.deliver(who, messages.resolve(who, rulesInForce.config().denyMessage(), Map.of()));
    }

    /** The player's current world name, or {@code null} when it cannot be read (falls back to the base rule set). */
    private static @Nullable String worldName(Player player) {
        World world = player.getWorld();
        return world == null ? null : world.getName();
    }
}
