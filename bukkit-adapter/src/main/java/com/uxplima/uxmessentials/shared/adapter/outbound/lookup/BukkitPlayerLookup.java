package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link PlayerLookup} implementation. Name resolution returns the connected player when one
 * matches exactly; UUID resolution returns the online player when present, else the profile-cache
 * entry as a {@link PlayerRef}. No live {@code Player} crosses the boundary — only the value object.
 */
@NullMarked
public final class BukkitPlayerLookup implements PlayerLookup {

    @Override
    public Optional<PlayerRef> findOnlineByName(String name) {
        Objects.requireNonNull(name, "name");
        Player player = Bukkit.getPlayerExact(name);
        return player == null ? Optional.empty() : Optional.of(BukkitRefs.toRef(player));
    }

    @Override
    public Optional<PlayerRef> findByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return Optional.of(BukkitRefs.toRef(online));
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name == null ? Optional.empty() : Optional.of(new PlayerRef(uuid, name));
    }

    @Override
    public boolean isOnline(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.isOnline();
    }
}
