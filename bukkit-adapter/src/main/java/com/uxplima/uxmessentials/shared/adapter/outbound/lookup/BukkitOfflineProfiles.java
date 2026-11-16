package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link OfflineProfiles} the server keeps: its user cache and its player data.
 *
 * <p>A name is looked up only in what the server has cached, never through the blocking Mojang lookup
 * {@code getOfflinePlayer(String)} makes for an unknown name on an online-mode server: the callers are commands and
 * use cases on the async lane, where a web round-trip would hold the lane for a typo. The plugin's own name index
 * answers first anyway, for every account that joined since it existed. A name is only accepted for an account that
 * has played here, so a typo cannot make up an owner.
 */
@NullMarked
public final class BukkitOfflineProfiles implements OfflineProfiles {

    @Override
    public Optional<PlayerRef> byName(String name) {
        Objects.requireNonNull(name, "name");
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached == null) {
            return Optional.empty();
        }
        String resolved = cached.getName();
        if (!cached.hasPlayedBefore() || resolved == null) {
            return Optional.empty();
        }
        return Optional.of(new PlayerRef(cached.getUniqueId(), resolved));
    }

    @Override
    public Optional<PlayerRef> byUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? Optional.empty() : Optional.of(new PlayerRef(uuid, name));
    }
}
