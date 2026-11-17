package com.uxplima.uxmessentials.economy.application.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The item deliveries a sweep over an item currency queued: one per player, handed over in their inventory when they
 * are online on a server of the database.
 *
 * <p>A delivery is claimed before it is handed over and changed or removed only under that claim afterwards, so two
 * servers delivering at once, or a quick rejoin, hand it over once. A claim older than a stale window can be taken
 * again, so a server that stopped between the claim and the hand-over does not strand it. What did not fit in the
 * inventory stays queued as a held-back delivery, handed over on the player's next join.
 */
public interface ItemDeliveryQueue {

    /**
     * {@code owner}'s open deliveries, oldest first. {@code withHeldBack} adds the ones held back because they did not
     * fit, which a join hands over and a pass over the players online does not, so a full inventory is not asked again
     * every few seconds.
     */
    List<ItemDelivery> claimable(UUID owner, boolean withHeldBack);

    /** Of {@code owners}, the ones with an open delivery that is not held back. */
    List<UUID> waiting(List<UUID> owners);

    /** Claim the delivery for {@code claimant}; false when another claimant holds it or it is gone. */
    boolean claim(String id, String claimant);

    /**
     * Finish a claimed delivery: remove it when {@code rest} is zero, else keep {@code rest} of it as a held-back
     * credit and release the claim. False when the claim was lost.
     */
    boolean finish(String id, String claimant, BigDecimal rest);

    /** Give up a claim without changing the delivery. */
    void release(String id, String claimant);

    /** What a delivery does to the player's held amount of the currency. */
    enum Kind {
        /** Add the amount. */
        GIVE,
        /** Set the held amount to the amount. */
        RESET
    }

    /** One queued delivery. */
    record ItemDelivery(String id, UUID owner, String currency, Kind kind, BigDecimal amount) {

        public ItemDelivery {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(amount, "amount");
        }
    }
}
