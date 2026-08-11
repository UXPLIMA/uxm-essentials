package com.uxplima.uxmessentials.api.bukkit.event.trade;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A trade ended without a swap.
 *
 * <p>Fires once both sides have their stakes back, so nothing is in flight by the time a listener hears it. Which
 * side ended it is not carried: a cancel, a closed window and a disconnect all reach the same path.
 */
@NullMarked
public final class UxmTradeCancelEvent extends UxmEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID tradeId;
    private final UUID initiatorId;
    private final String initiatorName;
    private final UUID partnerId;
    private final String partnerName;

    public UxmTradeCancelEvent(
            UUID tradeId, UUID initiatorId, String initiatorName, UUID partnerId, String partnerName) {
        this.tradeId = Objects.requireNonNull(tradeId, "tradeId");
        this.initiatorId = Objects.requireNonNull(initiatorId, "initiatorId");
        this.initiatorName = Objects.requireNonNull(initiatorName, "initiatorName");
        this.partnerId = Objects.requireNonNull(partnerId, "partnerId");
        this.partnerName = Objects.requireNonNull(partnerName, "partnerName");
    }

    /** The trade's own id. */
    public UUID getTradeId() {
        return tradeId;
    }

    /** The player who opened the trade. */
    public UUID getInitiatorId() {
        return initiatorId;
    }

    /** Their name. */
    public String getInitiatorName() {
        return initiatorName;
    }

    /** The player who accepted it. */
    public UUID getPartnerId() {
        return partnerId;
    }

    /** Their name. */
    public String getPartnerName() {
        return partnerName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
