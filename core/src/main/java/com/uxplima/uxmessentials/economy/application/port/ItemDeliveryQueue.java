package com.uxplima.uxmessentials.economy.application.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.EconomyReason;

/**
 * The deliveries a sweep queued for balances that can only change while their player is online (items in an
 * inventory, experience): one per player and currency, handed over when they are online on a server of the database.
 *
 * <p>A delivery is claimed before it is handed over and changed or removed only under that claim afterwards, so two
 * servers delivering at once, or a quick rejoin, hand it over once. A claim older than a stale window can be taken
 * again, so a server that stopped between the claim and the hand-over does not strand it; the claimant refreshes its
 * claim while it waits to record a hand-over, so a live claim never goes stale. What did not fit stays queued as a
 * held-back give, handed over on the player's next join.
 *
 * <p>A give queued for a player who already has an unclaimed give of the same currency and reason waiting is added to
 * it, so the queue holds at most one waiting give per player and currency however many sweeps run while they are
 * away. A reset removes everything waiting of its currency for the player first, so a give that waits beside a reset
 * was queued after it.
 */
public interface ItemDeliveryQueue {

    /**
     * {@code owner}'s open deliveries. {@code withHeldBack} adds the ones held back because they did not fit, which a
     * join hands over and a pass over the players online does not, so a full inventory is not asked again every few
     * seconds.
     */
    List<ItemDelivery> claimable(UUID owner, boolean withHeldBack);

    /** Of {@code owners}, the ones with an open delivery that is not held back. */
    List<UUID> waiting(List<UUID> owners);

    /** Whether {@code owner} has a reset of {@code currency} waiting, which credits of that currency must wait for. */
    boolean resetWaiting(UUID owner, String currency);

    /**
     * Queue a give of {@code amount} for each of {@code owners}, under the id of what queued it, merged into a waiting
     * give as the type says.
     */
    void queueGive(String sweepId, List<UUID> owners, String currency, BigDecimal amount, EconomyReason reason);

    /**
     * Claim the delivery for {@code claimant} and read it back as claimed, so an amount merged into it before the
     * claim is the one handed over. Empty when another claimant holds it or it is gone.
     */
    Optional<ItemDelivery> claim(String id, String claimant);

    /** Refresh the claim's time, so it does not go stale while its claimant still holds it. False when lost. */
    boolean touch(String id, String claimant);

    /**
     * Finish a claimed delivery: remove it when {@code rest} is zero, else keep {@code rest} of it as a held-back give
     * and release the claim. False when the claim was lost or the delivery is gone.
     */
    boolean finish(String id, String claimant, BigDecimal rest);

    /** Give up a claim without changing the delivery. */
    void release(String id, String claimant);

    /** What a delivery does to the player's balance. */
    enum Kind {
        /** Add the amount. */
        GIVE,
        /** Set the balance to the amount. */
        RESET
    }

    /** One queued delivery; {@code reason} is what the ledger row says when it is handed over. */
    record ItemDelivery(String id, UUID owner, String currency, Kind kind, BigDecimal amount, EconomyReason reason) {

        public ItemDelivery {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
