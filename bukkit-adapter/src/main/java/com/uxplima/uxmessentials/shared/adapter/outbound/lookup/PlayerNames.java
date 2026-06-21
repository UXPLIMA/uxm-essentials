package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the uuid-to-display-name resolver the rendering seams use when a stored row carries only a player's
 * uuid (warps and player-warps persist the owner uuid, not the name). The resolver prefers the online player's
 * live name, falls back to a played-before profile name, and finally to the uuid string when the server has
 * never seen the account. It is the one place that fallback policy lives, so every owner line resolves alike.
 */
@NullMarked
public final class PlayerNames {

    private PlayerNames() {}

    /** A resolver mapping a player uuid to its best-known display name, falling back to the uuid string. */
    public static Function<UUID, String> resolver(PlayerLookup lookup) {
        Objects.requireNonNull(lookup, "lookup");
        return uuid -> lookup.findByUuid(uuid).map(PlayerRef::name).orElse(uuid.toString());
    }
}
