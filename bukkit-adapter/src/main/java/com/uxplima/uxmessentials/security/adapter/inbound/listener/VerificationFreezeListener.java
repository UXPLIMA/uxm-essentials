package com.uxplima.uxmessentials.security.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import io.papermc.paper.event.player.AsyncChatEvent;

import com.uxplima.uxmessentials.security.adapter.VerificationSessions;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Pins a frozen player in place until they verify: while a player is a pending {@link VerificationSessions} entry,
 * their movement across a block, commands, chat, interactions, item drops, and block edits are all cancelled — the
 * only thing they can do is tap the keypad. A relog re-runs the join decision and a successful verification clears the
 * pending entry, so this listener naturally stops cancelling the moment the player is no longer frozen.
 *
 * <p>An attempted command or chat line — a deliberate action, unlike incidental movement — draws the "verify first"
 * nudge so the player understands why nothing happened; the silent physical cancels (move, interact, drop, block) do
 * not spam it. Head-only movement (yaw/pitch) is left alone so a frozen player can still look around.
 */
@NullMarked
public final class VerificationFreezeListener implements Listener {

    private final VerificationSessions sessions;
    private final Messages messages;
    private final MessageSink sink;

    public VerificationFreezeListener(VerificationSessions sessions, Messages messages, MessageSink sink) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (frozen(event.getPlayer()) && movedBlock(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (frozen(event.getPlayer())) {
            event.setCancelled(true);
            nudge(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        // The TOTP-via-chat capture runs at LOWEST and cancels first, so ignoreCancelled skips it here; any other
        // chat line from a frozen player is swallowed and nudged.
        if (frozen(event.getPlayer())) {
            event.setCancelled(true);
            nudge(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (frozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (frozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (frozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (frozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (frozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean frozen(Player player) {
        return sessions.isPending(player.getUniqueId());
    }

    private void nudge(Player player) {
        PlayerRef ref = BukkitRefs.toRef(player);
        sink.deliver(ref, messages.resolve(ref, SecurityMessageKey.SECURITY_VERIFY_MUST_VERIFY, Map.of()));
    }

    private static boolean movedBlock(PlayerMoveEvent event) {
        var from = event.getFrom();
        var to = event.getTo();
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }
}
