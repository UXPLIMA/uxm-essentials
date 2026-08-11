package com.uxplima.uxmessentials.api.bukkit.event.trade;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import org.jspecify.annotations.NullMarked;

/**
 * Two players finished a trade.
 *
 * <p>Fires once the swap has settled, so the items, money and experience have already moved. The figures are what
 * each side gave, which is what the other side received.
 *
 * <p>This extends {@link UxmEvent} rather than the player event: a trade has two subjects, and naming one of them
 * as "the" player would put the other one in second place.
 */
@NullMarked
public final class UxmTradeCompleteEvent extends UxmEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID tradeId;
    private final UUID initiatorId;
    private final String initiatorName;
    private final UUID partnerId;
    private final String partnerName;
    private final int initiatorItems;
    private final int partnerItems;
    private final List<UxmMoney> initiatorMoney;
    private final List<UxmMoney> partnerMoney;
    private final long initiatorExperience;
    private final long partnerExperience;

    public UxmTradeCompleteEvent(
            UUID tradeId,
            UUID initiatorId,
            String initiatorName,
            UUID partnerId,
            String partnerName,
            int initiatorItems,
            int partnerItems,
            List<UxmMoney> initiatorMoney,
            List<UxmMoney> partnerMoney,
            long initiatorExperience,
            long partnerExperience) {
        this.tradeId = Objects.requireNonNull(tradeId, "tradeId");
        this.initiatorId = Objects.requireNonNull(initiatorId, "initiatorId");
        this.initiatorName = Objects.requireNonNull(initiatorName, "initiatorName");
        this.partnerId = Objects.requireNonNull(partnerId, "partnerId");
        this.partnerName = Objects.requireNonNull(partnerName, "partnerName");
        this.initiatorItems = initiatorItems;
        this.partnerItems = partnerItems;
        this.initiatorMoney = List.copyOf(initiatorMoney);
        this.partnerMoney = List.copyOf(partnerMoney);
        this.initiatorExperience = initiatorExperience;
        this.partnerExperience = partnerExperience;
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

    /** How many items the initiator gave. */
    public int getInitiatorItems() {
        return initiatorItems;
    }

    /** How many items the partner gave. */
    public int getPartnerItems() {
        return partnerItems;
    }

    /** What the initiator gave, one entry per currency they staked. */
    public List<UxmMoney> getInitiatorMoney() {
        return initiatorMoney;
    }

    /** What the partner gave, one entry per currency they staked. */
    public List<UxmMoney> getPartnerMoney() {
        return partnerMoney;
    }

    /** The experience points the initiator gave. */
    public long getInitiatorExperience() {
        return initiatorExperience;
    }

    /** The experience points the partner gave. */
    public long getPartnerExperience() {
        return partnerExperience;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
