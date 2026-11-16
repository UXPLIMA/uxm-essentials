package com.uxplima.uxmessentials.migration;

import java.time.Instant;
import java.util.Set;

import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Where an import records the one-time kits each player already claimed in the source plugin, so moving to this plugin
 * does not hand them out again. A recorded claim is never replaced: the first record of a player and a kit stands,
 * whatever the conflict policy, because a claim carries no setting an overwrite could improve. Every method does I/O
 * and runs off the tick.
 */
@NullMarked
public interface KitClaimTarget {

    /** The one-time kits {@code player} is recorded as having claimed, read without changing anything. */
    Set<KitId> claimed(PlayerRef player);

    /**
     * Record that {@code player} claimed the one-time kit {@code kit} at {@code claimedAt}, unless a claim is already
     * recorded. True only when this call recorded it.
     */
    boolean record(PlayerRef player, KitId kit, Instant claimedAt);
}
