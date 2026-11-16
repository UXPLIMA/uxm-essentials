package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

import java.io.IOException;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;

/**
 * Turns a stored item compound into an item and back, the way the server itself does. It is the one part of reading
 * player data that needs the server's internals, which is why it is separate from the file handling.
 */
public interface ItemCodec {

    /** The data version this server writes, which a stored item must have been written with to be edited here. */
    int dataVersion();

    /** The item {@code compound} holds, or empty when this server cannot read it. */
    Optional<ItemStack> decode(TagPayload compound) throws IOException;

    /** The compound payload for {@code item}, which is not empty, without a slot tag. */
    TagPayload encode(ItemStack item) throws IOException;
}
