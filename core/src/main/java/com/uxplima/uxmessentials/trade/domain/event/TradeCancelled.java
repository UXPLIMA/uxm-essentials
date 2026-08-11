package com.uxplima.uxmessentials.trade.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.domain.TradeId;

/**
 * A trade ended without a swap: somebody cancelled, closed the window, or disconnected.
 *
 * <p>Published after both sides have their stakes back, so nothing is in flight by the time a listener hears it.
 * Which of the two ended it is not carried: a window closing and a player quitting reach the same path, and naming
 * one of them would be a guess.
 *
 * @param id the trade's own id
 * @param initiator the player who opened the trade
 * @param partner the player who accepted it
 */
public record TradeCancelled(TradeId id, PlayerRef initiator, PlayerRef partner) implements TradeEvent {

    public TradeCancelled {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(partner, "partner");
    }
}
