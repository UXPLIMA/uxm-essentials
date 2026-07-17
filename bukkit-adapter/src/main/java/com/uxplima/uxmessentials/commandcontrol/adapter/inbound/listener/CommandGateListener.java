package com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSource;
import com.uxplima.uxmessentials.commandcontrol.application.CommandControlMessageKey;
import com.uxplima.uxmessentials.commandcontrol.domain.NamespaceBypassRule;
import com.uxplima.uxmessentials.commandcontrol.domain.PlayerFacts;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The command whitelist / blacklist gate: on {@link PlayerCommandPreprocessEvent} it extracts the command root
 * (strip leading {@code /}, drop the arguments, lowercase), consults the pure {@link RuleSet} with the player's facts
 * (their group via the {@link PlayerGroupSource}, permission checks via Bukkit), and on {@link RuleSet.Decision#DENY}
 * cancels the dispatch and sends the configured deny line — the vanilla-style "unknown command" so a hidden command
 * reads as nonexistent, or the "no permission" line, per config.
 *
 * <p>The gate also closes the namespace-bypass hole: a non-bypass player who prefixes a denied command with its
 * namespace — {@code /minecraft:gamemode}, {@code /plugin:cmd} — reaches the same handler as the bare command yet reads
 * as a different root, so the {@link RuleSet} alone would let it through. The {@link NamespaceBypassRule} strips the
 * prefix and re-asks the rule set about the bare form, so {@code /minecraft:gamemode} is blocked exactly when
 * {@code /gamemode} is. The plugin-hide half of the escape is handled in the visibility listener, whose
 * {@code HidePolicy} already folds the {@code namespace:} prefix.
 *
 * <p>Runs at {@link EventPriority#HIGH} so cooperating plugins at NORMAL still see an uncancelled event but the
 * dispatch is stopped before the vanilla/dispatcher handler. The console is never gated — this listens only to the
 * player-command event — and a {@code .bypass} holder is always allowed (the {@code RuleSet} short-circuits on the
 * bypass node before any group lookup). When the rule set is inert (a blacklist with empty lists) the listener
 * short-circuits before any per-command work, so an operator who leaves the lists blank pays nothing.
 *
 * <p>The gate is client-agnostic: {@link PlayerCommandPreprocessEvent} fires for a Bedrock / Geyser player (routed
 * through Floodgate as an ordinary {@code Player}) exactly as for a Java client, so the filter needs no client probe
 * and no Geyser soft-dependency. It also runs per backend: a command a proxy forwards to this server is gated on
 * arrival here, so cross-server setups gate on each backend rather than at the proxy.
 */
@NullMarked
public final class CommandGateListener implements Listener {

    private final RuleSet rules;
    private final NamespaceBypassRule namespaceBypass;
    private final PlayerGroupSource groups;
    private final Messages messages;
    private final MessageSink sink;
    private final CommandControlMessageKey denyMessage;

    public CommandGateListener(
            RuleSet rules,
            NamespaceBypassRule namespaceBypass,
            PlayerGroupSource groups,
            Messages messages,
            MessageSink sink,
            CommandControlMessageKey denyMessage) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.namespaceBypass = Objects.requireNonNull(namespaceBypass, "namespaceBypass");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.denyMessage = Objects.requireNonNull(denyMessage, "denyMessage");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (rules.isInert()) {
            return;
        }
        String root = commandRoot(event.getMessage());
        if (root.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        PlayerFacts facts = new BukkitPlayerFacts(player, groups);
        if (!isDenied(root, facts)) {
            return;
        }
        event.setCancelled(true);
        PlayerRef who = BukkitRefs.toRef(player);
        sink.deliver(who, messages.resolve(who, denyMessage, Map.of()));
    }

    /** A command root is denied when the rule set denies it, or when it is a namespace-prefixed denied command. */
    private boolean isDenied(String root, PlayerFacts facts) {
        return rules.decide(root, facts) == RuleSet.Decision.DENY || namespaceBypass.deniesNamespacedForm(root, facts);
    }

    /** The command label: leading slash stripped, arguments dropped, lowercased. */
    private static String commandRoot(String rawMessage) {
        String body = rawMessage.startsWith("/") ? rawMessage.substring(1) : rawMessage;
        int space = body.indexOf(' ');
        String label = space < 0 ? body : body.substring(0, space);
        return label.toLowerCase(Locale.ROOT);
    }
}
