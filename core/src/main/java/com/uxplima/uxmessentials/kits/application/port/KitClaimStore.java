package com.uxplima.uxmessentials.kits.application.port;

import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Per-player one-time-claim state for kits. A one-time kit is consumed forever after the first claim; this
 * port records and reads that fact. On a single server the adapter keeps the stamp in PDC under a per-kit
 * {@link org.bukkit.NamespacedKey NamespacedKey} (created once), since a claim flag is transient per-holder
 * state that may safely die with a world rollback; claims the database records for a player there (an import's) are
 * copied into PDC as they join. With {@code network.enabled} on it lives in the shared database instead, because each
 * backend has its own PDC and would grant the kit again.
 *
 * <p>This is intentionally narrow and separate from the shared {@code Cooldowns} port: cooldowns rate-limit
 * a repeatable kit (a "ready-at" timestamp that expires), while a one-time stamp is a permanent
 * "already-claimed" mark with no expiry. A kit can be both, rate-limited and one-time, and the two gates
 * are checked independently. The {@code reset} operations back {@code /kit reset}, clearing a player's
 * stamps so they may claim again.
 *
 * <p>An offline player has no PDC to read, so {@link #hasClaimed} reads {@code false} and {@link #markClaimed}
 * is a no-op for them: the same offline-no-op semantics the cooldown store has.
 *
 * <p>On a network the store is shared through the database, and two servers can both see a kit as unclaimed for a
 * player who just switched between them. {@link #acquire} is the seam for that: the claim use case takes the claim
 * before granting, and only the server that won it hands the kit over. The PDC store is written by one server only,
 * so its defaults answer at once and leave the claim to {@link #markClaimed} after the grant.
 */
public interface KitClaimStore {

    /** True when {@code who} has already consumed the one-time kit {@code kit}. */
    boolean hasClaimed(PlayerRef who, KitId kit);

    /** Record that {@code who} has consumed the one-time kit {@code kit}. */
    void markClaimed(PlayerRef who, KitId kit);

    /** Clear {@code who}'s one-time stamp for {@code kit}, so they may claim it again. */
    void reset(PlayerRef who, KitId kit);

    /** Clear every one-time stamp {@code who} holds, across all kits. */
    void resetAll(PlayerRef who);

    /**
     * Whether {@code who}'s stamps can be answered from fact. A shared store reads them when the player logs in and
     * answers false until then; {@link #hasClaimed} then reads true, so nothing is granted twice while it waits.
     */
    default boolean ready(PlayerRef who) {
        return true;
    }

    /**
     * Take {@code who}'s one-time claim on {@code kit} before the kit is granted. The future completes on the thread
     * that owns {@code who}: true when this server may grant the kit, false when another claim got there first. It
     * completes exceptionally when the store failed or the player left before the answer came back.
     */
    default CompletableFuture<Boolean> acquire(PlayerRef who, KitId kit) {
        return CompletableFuture.completedFuture(true);
    }

    /** Give back a claim {@link #acquire} took for a grant that did not go through. */
    default void release(PlayerRef who, KitId kit) {}
}
