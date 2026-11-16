package com.uxplima.uxmessentials.migration;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/** One-time kit claims kept in a map, standing in for the claim table: the first record of a player and kit stands. */
final class InMemoryKitClaims implements KitClaimTarget {

    private final Map<UUID, Map<KitId, Instant>> claims = new HashMap<>();

    Map<KitId, Instant> of(PlayerRef player) {
        return Map.copyOf(claims.getOrDefault(player.uuid(), Map.of()));
    }

    @Override
    public Set<KitId> claimed(PlayerRef player) {
        return Set.copyOf(claims.getOrDefault(player.uuid(), Map.of()).keySet());
    }

    @Override
    public boolean record(PlayerRef player, KitId kit, Instant claimedAt) {
        return claims.computeIfAbsent(player.uuid(), id -> new HashMap<>()).putIfAbsent(kit, claimedAt) == null;
    }
}
