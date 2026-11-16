package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

import org.jspecify.annotations.Nullable;

/**
 * One slot a save changes: the slot index in the window's order, and the item compound to put there, or {@code null}
 * to empty it.
 *
 * @param slot the slot index, as {@link ContainerLayout} numbers it
 * @param item the encoded item, without a slot tag, or {@code null} for an empty slot
 */
public record SlotWrite(int slot, @Nullable TagPayload item) {}
