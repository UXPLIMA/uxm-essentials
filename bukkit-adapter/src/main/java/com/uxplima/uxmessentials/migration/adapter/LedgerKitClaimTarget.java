package com.uxplima.uxmessentials.migration.adapter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.kits.application.LocalKitClaims;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.migration.KitClaimTarget;
import com.uxplima.uxmessentials.shared.application.port.PlayerClaimLedger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Records imported one-time kit claims in the database's claim table, the one a network reads its claims from and a
 * single server stamps into each player's PDC as they join. The table is on every storage backend whether the network
 * is on or not, so the import writes the same rows either way, and its unique key keeps a re-run from adding any.
 *
 * <p>On a single server a player who is online during the import has already joined, so a claim recorded for them is
 * also stamped through {@code local} at once. On a network the claim is the row itself: a server that read the
 * player's claims before the import still refuses the kit, because taking a claim inserts against the same key.
 */
@NullMarked
final class LedgerKitClaimTarget implements KitClaimTarget {

    private final PlayerClaimLedger ledger;
    private final Optional<LocalKitClaims> local;

    LedgerKitClaimTarget(PlayerClaimLedger ledger, Optional<LocalKitClaims> local) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.local = Objects.requireNonNull(local, "local");
    }

    @Override
    public Set<KitId> claimed(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        Set<KitId> kits = new HashSet<>();
        for (String id : ledger.kits(player.uuid())) {
            try {
                kits.add(KitId.of(id));
            } catch (IllegalArgumentException unusable) {
                // A row no kit id can name cannot match an imported claim either.
            }
        }
        return kits;
    }

    @Override
    public boolean record(PlayerRef player, KitId kit, Instant claimedAt) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(kit, "kit");
        Objects.requireNonNull(claimedAt, "claimedAt");
        boolean landed = ledger.claimKit(player.uuid(), kit.value(), claimedAt.toEpochMilli());
        if (landed) {
            local.ifPresent(claims -> claims.stamp(player, kit));
        }
        return landed;
    }
}
