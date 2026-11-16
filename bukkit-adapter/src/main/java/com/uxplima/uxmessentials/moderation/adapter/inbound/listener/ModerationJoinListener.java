package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.moderation.application.ModerationSessions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import org.jspecify.annotations.NullMarked;

/**
 * The jail/seen lifecycle listener: the online-only jail countdown's connection edges.
 *
 * <ul>
 *   <li><b>Join</b>: record last-seen/last-IP, then re-apply a still-active jail (offline jail re-applied so
 *       a player cannot escape by relogging) and stamp the session start. Nothing is decremented yet. The address
 *       history behind /alts is not written here; the kernel IP-history recorder owns that one capture.
 *   <li><b>Quit</b>: burn the online time elapsed since the session start off an online-only sentence
 *       (frozen while offline), persisting the decremented remainder or releasing a served sentence, then
 *       record last-seen.
 * </ul>
 *
 * <p>Only the player's address is read here, on the player's own thread; {@link ModerationSessions} does the rest on
 * the storage lane. The login ban-enforcement is a separate HIGHEST {@code PlayerLoginEvent} listener; this one runs
 * on join (after the connection is allowed).
 */
@NullMarked
public final class ModerationJoinListener implements Listener {

    private final ModerationSessions sessions;

    public ModerationJoinListener(ModerationSessions sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        sessions.joined(BukkitRefs.toRef(event.getPlayer()), address(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sessions.left(BukkitRefs.toRef(event.getPlayer()), address(event.getPlayer()));
    }

    private static Optional<String> address(Player player) {
        InetSocketAddress socket = player.getAddress();
        return socket == null
                ? Optional.empty()
                : Optional.ofNullable(socket.getAddress()).map(InetAddress::getHostAddress);
    }
}
