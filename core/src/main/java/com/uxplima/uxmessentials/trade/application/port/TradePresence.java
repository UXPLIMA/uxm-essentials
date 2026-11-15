package com.uxplima.uxmessentials.trade.application.port;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * What this backend knows about its own players for a cross-server trade invite: whether the invited name is online
 * here, whether a player is still here, and whether they are already in a trade. A platform boundary (the adapter
 * reads the live server and both trade-window registries) and the test seam the invite flow is driven through.
 */
public interface TradePresence {

    /** The player online on this backend under {@code name}, matched without regard to case. */
    Optional<PlayerRef> onlineByName(String name);

    /** Whether {@code player} is online on this backend. */
    boolean isOnline(UUID player);

    /** Whether {@code player} is already in a same-server or cross-server trade on this backend. */
    boolean isTrading(UUID player);
}
