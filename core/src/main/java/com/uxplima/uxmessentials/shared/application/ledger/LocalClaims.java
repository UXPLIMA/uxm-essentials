package com.uxplima.uxmessentials.shared.application.ledger;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What one server's PDC says a player has claimed, read when they first join with the network on so the database
 * starts from it instead of from nothing.
 *
 * @param kits the ids of the one-time kits the PDC marks as claimed
 * @param cooldowns when each PDC cooldown ends, in epoch milliseconds, by normalized scope
 */
public record LocalClaims(Set<String> kits, Map<String, Long> cooldowns) {

    public LocalClaims {
        kits = Set.copyOf(Objects.requireNonNull(kits, "kits"));
        cooldowns = Map.copyOf(Objects.requireNonNull(cooldowns, "cooldowns"));
    }

    /** Whether there is nothing to carry. */
    public boolean isEmpty() {
        return kits.isEmpty() && cooldowns.isEmpty();
    }
}
