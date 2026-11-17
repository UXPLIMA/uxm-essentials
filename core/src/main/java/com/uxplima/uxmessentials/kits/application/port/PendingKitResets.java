package com.uxplima.uxmessentials.kits.application.port;

import java.util.Set;
import java.util.UUID;

/**
 * Kit resets kept for a player who was not online when an operator ran {@code /kit reset} on a single server. Their
 * one-time stamps live in their own player data, which only exists while they are online, so the reset is applied as
 * they next join. Both methods do storage IO and run off the tick.
 */
public interface PendingKitResets {

    /** The kit id that stands for every kit the player holds. */
    String ALL = "*";

    /** Keep a reset of {@code kit} for {@code player}: a kit id, or {@link #ALL}. */
    void keep(UUID player, String kit);

    /** Remove and return every reset kept for {@code player}: kit ids, and {@link #ALL} when one cleared them all. */
    Set<String> take(UUID player);
}
