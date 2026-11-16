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
 */
public interface TradeItemReturns {

    /** Keep {@code items} for {@code owner}, on top of anything already kept for them. */
    void keep(UUID owner, VaultContents items);

    /** Everything kept for {@code owner}, oldest first, without removing it. Empty when nothing is kept. */
    List<Kept> kept(UUID owner);

    /**
     * Remove the kept batch {@code id} and report whether this call removed it. Only the caller that sees {@code true}
     * hands the batch over, so it goes to exactly one caller even when two servers deliver at once.
     */
    boolean claim(String id);

    /**
     * One kept batch.
     *
     * @param id the batch's key, for {@link #claim}
     * @param items the items
     */
    record Kept(String id, VaultContents items) {

        public Kept {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(items, "items");
        }
    }
}
