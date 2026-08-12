package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam for the trade placeholders: whether a player is in a live exchange, and whether two players are in
 * one with each other. A same-server trade is transient in-memory state held by the session registry, so this
 * reads that registry directly; when the trade module is disabled the seam is absent and every trade key reads
 * as "no one is trading".
 */
public interface TradePlaceholders {

    /** True when {@code who} has a trade window open with anybody. */
    boolean isTrading(PlayerRef who);

    /** True when {@code one} and {@code other} are the two sides of the same live trade. */
    boolean isTradingWith(PlayerRef one, PlayerRef other);
}
