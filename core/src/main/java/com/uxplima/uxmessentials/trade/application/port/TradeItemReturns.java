package com.uxplima.uxmessentials.trade.application.port;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.vaults.domain.VaultContents;

/**
 * Durable keeping for the items a same-server trade had to hand back to a player who was offline or unreachable by
 * then. They wait here and are handed over on the player's next join, on any server sharing the database. The items
 * are held in the vaults' serialized form ({@link VaultContents}), so an enchantment, a name or a shulker's contents
 * survive the wait. A port because it is a storage boundary; every call does I/O and runs off the tick.
 *
 * <p>A delivery claims a batch with a token of its own before handing it over, and removes or shrinks it only under
 * that token afterwards, so the row is never gone before the items are in an inventory. A claim older than the
 * store's stale window counts as abandoned (its server crashed between the claim and the hand-over) and can be taken
 * again.
 */
public interface TradeItemReturns {

    /** Keep {@code items} for {@code owner}, on top of anything already kept for them. */
    void keep(UUID owner, VaultContents items);

    /**
     * {@code owner}'s batches that can be claimed now, oldest first: the unclaimed ones and those whose claim went
     * stale. A row whose items cannot be read is skipped and left in place. Empty when nothing is kept.
     */
    List<Kept> claimable(UUID owner);

    /**
     * Claim batch {@code id} for {@code claimant} if it is unclaimed or its claim went stale, and report whether this
     * call claimed it. Only the caller that sees {@code true} hands the batch over.
     */
    boolean claim(String id, String claimant);

    /**
     * Finish a hand-over: remove batch {@code id} when {@code rest} is empty, otherwise put {@code rest} (what did not
     * fit) in its place and release the claim. Applies only while {@code claimant} still holds the claim; false when
     * it no longer does.
     */
    boolean finish(String id, String claimant, VaultContents rest);

    /** Give up {@code claimant}'s claim on batch {@code id}, untouched, so a later join can take it. */
    void release(String id, String claimant);

    /**
     * One kept batch.
     *
     * @param id the batch's key
     * @param items the items
     */
    record Kept(String id, VaultContents items) {

        public Kept {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(items, "items");
        }
    }
}
