package com.uxplima.uxmessentials.playerstate.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.playerstate.application.WorldCommandPolicy;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Enforces the per-world command blocker: when a player runs a command listed under their current world (or
 * under the {@code "*"} wildcard) in {@code world-command-blocks}, the dispatch is cancelled before the
 * command runs and the player is told the command is disabled here. A player holding the bypass node
 * {@code uxmessentials.world.command-bypass} is never blocked.
 *
 * <p>The label is the first token after the slash, case-folded, with any {@code uxmessentials:} namespace
 * prefix stripped so {@code /uxmessentials:tpa} is caught the same as {@code /tpa}. Runs at
 * {@link EventPriority#HIGH} so cooperating plugins at NORMAL still see an uncancelled event but the dispatch
 * is stopped before the vanilla/dispatcher handler. When the configured map is empty the listener
 * short-circuits before reading the player's world, so an operator who leaves the map blank pays nothing.
 */
@NullMarked
public final class WorldCommandListener implements Listener {

    /** The node that exempts a player from every per-world command block. */
    public static final String BYPASS_NODE = "uxmessentials.world.command-bypass";

    private final WorldCommandPolicy policy;
    private final Messages messages;
    private final MessageSink sink;

    public WorldCommandListener(WorldCommandPolicy policy, Messages messages, MessageSink sink) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (policy.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS_NODE)) {
            return;
        }
        if (!policy.isBlocked(player.getWorld().getName(), label(event.getMessage()))) {
            return;
        }
        event.setCancelled(true);
        PlayerRef who = BukkitRefs.toRef(player);
        sink.deliver(who, messages.resolve(who, PlayerstateMessageKey.WORLD_COMMAND_BLOCKED, Map.of()));
    }

    private static String label(String rawMessage) {
        String body = rawMessage.startsWith("/") ? rawMessage.substring(1) : rawMessage;
        int space = body.indexOf(' ');
        return space < 0 ? body : body.substring(0, space);
    }
}
