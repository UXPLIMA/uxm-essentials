package com.uxplima.uxmessentials.economy.application.port;

import java.util.Map;
import java.util.UUID;

/**
 * Durable keeping for items a sale took from a seller but did not pay for, held while the seller is not online to
 * take them back. A port because it is a storage boundary. Items are plain stacks named by their lower case material
 * id, which is all a sale ever takes. Every call does I/O and runs off the tick.
 */
public interface ItemReturnStore {

    /** Keep {@code items} (item id to count) for {@code owner}, on top of anything already kept for them. */
    void keep(UUID owner, Map<String, Integer> items);

    /**
     * Take everything kept for {@code owner}, summed per item id, and remove it. Each kept row goes to exactly one
     * caller, even when two takes run at once on servers sharing the database. Empty when nothing is kept.
     */
    Map<String, Integer> takeAll(UUID owner);
}
