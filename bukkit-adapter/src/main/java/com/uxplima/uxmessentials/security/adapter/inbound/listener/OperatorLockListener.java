package com.uxplima.uxmessentials.security.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;

import com.uxplima.uxmessentials.security.adapter.OperatorLock;
import org.jspecify.annotations.NullMarked;

/**
 * The events behind the {@code allowed-operators} list: an unlisted operator is stopped as they join, before any later
 * join listener sees them as an operator, and an {@code /op} naming somebody unlisted is refused from a player and
 * from a player, the console and RCON alike. It runs before the op-command re-authentication, so a refused {@code /op}
 * asks for no code. Whatever command does run, the online operators are checked again a tick later, which catches the
 * routes the text cannot show, such as {@code /execute ... run op}.
 */
@NullMarked
public final class OperatorLockListener implements Listener {

    private final OperatorLock lock;

    public OperatorLockListener(OperatorLock lock) {
        this.lock = Objects.requireNonNull(lock, "lock");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        lock.enforce(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        // An operator made one since the last sweep runs nothing as an operator: they are stopped here first.
        if (lock.enforce(event.getPlayer()) || lock.refuses(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (lock.refuses(event.getSender(), event.getCommand())) {
            event.setCancelled(true);
        }
    }

    // RCON commands arrive on an event with a handler list of its own, so the console handler never sees them.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onRemoteCommand(RemoteServerCommandEvent event) {
        if (lock.refuses(event.getSender(), event.getCommand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterPlayerCommand(PlayerCommandPreprocessEvent event) {
        lock.sweepSoon();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterServerCommand(ServerCommandEvent event) {
        lock.sweepSoon();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterRemoteCommand(RemoteServerCommandEvent event) {
        lock.sweepSoon();
    }
}
