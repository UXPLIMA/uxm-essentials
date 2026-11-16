package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves a wallet owner's last-seen time for the purge filter. A currently-online player reports "now" so the
 * maintenance task never purges someone who is playing; otherwise the value is the offline player's recorded
 * last-seen ({@code 0} when the server has never seen them, which the task treats as "don't purge").
 * {@code getOfflinePlayer(UUID)} is the non-blocking, UUID-keyed lookup, no Mojang round-trip, so it is safe on
 * the async lane the task runs on. Whether the player is online comes from the online index, which any thread may
 * ask; the server's own player list is a tick thread's to read.
 */
@NullMarked
public final class BukkitPlayerLastSeen implements EconomyMaintenanceTask.PlayerLastSeen {

    private final PlayerLookup players;

    public BukkitPlayerLastSeen(PlayerLookup players) {
        this.players = Objects.requireNonNull(players, "players");
    }

    @Override
    public long lastSeenMillis(UUID uuid) {
        if (players.isOnline(uuid)) {
            return System.currentTimeMillis();
        }
        return Bukkit.getOfflinePlayer(uuid).getLastSeen();
    }
}
