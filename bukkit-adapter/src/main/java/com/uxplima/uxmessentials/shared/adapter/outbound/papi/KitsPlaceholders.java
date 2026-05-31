package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Duration;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code kit_cooldown_<kitid>} placeholder. It is an adapter over
 * the kits context's {@code KitRepository} (to resolve the kit's cooldown tier) and the shared {@code
 * Cooldowns} gate, wired during bootstrap; when the kits module is disabled the seam is absent and the
 * placeholder degrades.
 *
 * <p>The remaining duration is empty when the kit id is unknown (no such kit) or when the player is off
 * cooldown (ready now) — the expansion renders {@code 0s} in the ready case and the empty default for an
 * unknown kit.
 */
public interface KitsPlaceholders {

    /**
     * The cooldown remaining for {@code who} on the kit named {@code kitId}: present and positive while
     * the player must wait, present-and-zero when the kit exists but is ready, and empty when no kit
     * carries that id.
     */
    Optional<Duration> cooldownRemaining(PlayerRef who, String kitId);
}
