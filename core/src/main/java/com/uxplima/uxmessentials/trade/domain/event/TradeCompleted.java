package com.uxplima.uxmessentials.trade.domain.event;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.domain.TradeId;

/**
 * Two players finished a trade: both confirmed, and the items, money and experience have all moved.
 *
 * <p>Published after the swap settles, so what it describes is done. The figures are what each side gave, which is
 * what the other side received.
 *
 * @param id the trade's own id
 * @param initiator the player who opened the trade
 * @param partner the player who accepted it
 * @param initiatorItems the total item quantity the initiator gave
 * @param partnerItems the total item quantity the partner gave
 * @param initiatorMoney what the initiator gave, per currency id
 * @param partnerMoney what the partner gave, per currency id
 * @param initiatorExperience the experience points the initiator gave
 * @param partnerExperience the experience points the partner gave
 */
public record TradeCompleted(
        TradeId id,
        PlayerRef initiator,
        PlayerRef partner,
        int initiatorItems,
        int partnerItems,
        Map<String, BigDecimal> initiatorMoney,
        Map<String, BigDecimal> partnerMoney,
        long initiatorExperience,
        long partnerExperience)
        implements TradeEvent {

    public TradeCompleted {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(partner, "partner");
        initiatorMoney = Map.copyOf(initiatorMoney);
        partnerMoney = Map.copyOf(partnerMoney);
        if (initiatorItems < 0 || partnerItems < 0) {
            throw new IllegalArgumentException("item counts must not be negative");
        }
        if (initiatorExperience < 0 || partnerExperience < 0) {
            throw new IllegalArgumentException("experience must not be negative");
        }
    }
}
