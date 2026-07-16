package com.uxplima.uxmessentials.trade.application;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.domain.OfferedItem;
import com.uxplima.uxmessentials.trade.domain.TradeOffer;
import com.uxplima.uxmessentials.trade.domain.TradeSession;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import org.jspecify.annotations.NullMarked;

/**
 * A settled trade's summary — who traded whom and what each side gave — built from a committed {@link TradeSession} for
 * the audit line. Item counts are the total quantity each side staked (the sum of the offered stacks' amounts), and the
 * money maps are each side's staked amount per currency id. It carries no Bukkit item, only the domain's own view, so it
 * stays in the pure application layer.
 *
 * @param initiator the player who opened the trade
 * @param partner the player who accepted it
 * @param initiatorItems the total item quantity the initiator gave
 * @param partnerItems the total item quantity the partner gave
 * @param initiatorMoney the money the initiator staked, per currency id
 * @param partnerMoney the money the partner staked, per currency id
 */
@NullMarked
public record TradeReceipt(
        PlayerRef initiator,
        PlayerRef partner,
        int initiatorItems,
        int partnerItems,
        Map<String, BigDecimal> initiatorMoney,
        Map<String, BigDecimal> partnerMoney) {

    public TradeReceipt {
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(partner, "partner");
        Objects.requireNonNull(initiatorMoney, "initiatorMoney");
        Objects.requireNonNull(partnerMoney, "partnerMoney");
        initiatorMoney = Map.copyOf(initiatorMoney);
        partnerMoney = Map.copyOf(partnerMoney);
        if (initiatorItems < 0 || partnerItems < 0) {
            throw new IllegalArgumentException("item counts must not be negative");
        }
    }

    /** Summarise a committed session's two offers into a receipt. */
    public static TradeReceipt of(TradeSession session) {
        Objects.requireNonNull(session, "session");
        TradeOffer initiatorOffer = session.offer(TradeSide.INITIATOR);
        TradeOffer partnerOffer = session.offer(TradeSide.PARTNER);
        return new TradeReceipt(
                session.participant(TradeSide.INITIATOR),
                session.participant(TradeSide.PARTNER),
                totalItems(initiatorOffer),
                totalItems(partnerOffer),
                initiatorOffer.money(),
                partnerOffer.money());
    }

    private static int totalItems(TradeOffer offer) {
        int total = 0;
        for (OfferedItem item : offer.items()) {
            total += item.amount();
        }
        return total;
    }
}
