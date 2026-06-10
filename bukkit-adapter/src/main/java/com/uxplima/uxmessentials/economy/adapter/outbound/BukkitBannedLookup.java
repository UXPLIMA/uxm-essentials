package com.uxplima.uxmessentials.economy.adapter.outbound;

import org.bukkit.Bukkit;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The Bukkit-backed {@link BaltopSnapshots.BannedLookup}: an owner counts as banned when the server's profile ban
 * list holds them. Read off the {@code OfflinePlayer}, so it covers vanilla bans; it runs only where the baltop
 * snapshot is built (off-tick, bounded by the snapshot capacity), never on the hot {@code /baltop} path.
 */
@NullMarked
public final class BukkitBannedLookup implements BaltopSnapshots.BannedLookup {

    @Override
    public boolean isBanned(PlayerRef owner) {
        return Bukkit.getOfflinePlayer(owner.uuid()).isBanned();
    }
}
