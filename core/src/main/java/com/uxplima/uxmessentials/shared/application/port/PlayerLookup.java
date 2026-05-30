package com.uxplima.uxmessentials.shared.application.port;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port that resolves player identities without exposing a Bukkit {@code Player}.
 *
 * <p>The adapter looks players up by name or UUID and maps the result to a {@link PlayerRef}.
 * Resolution by name returns the online player when one matches; offline-name resolution that hits a
 * profile cache or the mojang lookup is the adapter's concern and may be empty. Application code never
 * iterates {@code Bukkit.getOnlinePlayers()} — it asks this port.
 */
public interface PlayerLookup {

    /** The online player with this exact name, if one is connected. */
    Optional<PlayerRef> findOnlineByName(String name);

    /** The player with this UUID, online or resolvable from the profile cache. */
    Optional<PlayerRef> findByUuid(UUID uuid);

    /** True when the player with this UUID is currently connected. */
    boolean isOnline(UUID uuid);
}
