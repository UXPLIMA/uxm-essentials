package com.uxplima.uxmessentials.trade.domain;

import java.util.Objects;

/**
 * One stack a player has staked into a trade, represented abstractly so the domain never sees a Bukkit
 * {@code ItemStack}. The {@code handle} is an opaque token the adapter maps back to a real item snapshot at the
 * boundary (a serialised stack, a slot reference, or an escrow-row key in the cross-server phase); the domain treats
 * it only as an identity and carries the {@code amount} so the model can reason about quantities without decoding it.
 *
 * @param handle the opaque, adapter-owned reference to the concrete item snapshot
 * @param amount how many of the item this entry stands for (at least one)
 */
public record OfferedItem(String handle, int amount) {

    public OfferedItem {
        Objects.requireNonNull(handle, "handle");
        if (handle.isBlank()) {
            throw new IllegalArgumentException("offered item handle must not be blank");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("offered item amount must be at least 1: " + amount);
        }
    }
}
