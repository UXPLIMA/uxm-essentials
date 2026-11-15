package com.uxplima.uxmessentials.economy.application.port;

import java.util.Map;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The live inventory of a seller, where unsold items go back. A port because it is the platform boundary: the server
 * owns the inventory. Called on the seller's own entity thread.
 */
public interface SellerInventory {

    /**
     * Put {@code items} (item id to count) back into the seller's inventory, dropping what does not fit at their feet.
     * Answers {@code false}, having given nothing, when the seller is not online.
     */
    boolean giveBack(PlayerRef seller, Map<String, Integer> items);
}
