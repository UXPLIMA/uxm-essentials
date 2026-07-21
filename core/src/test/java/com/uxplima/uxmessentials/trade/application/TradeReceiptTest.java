package com.uxplima.uxmessentials.trade.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.domain.OfferedItem;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import com.uxplima.uxmessentials.trade.domain.TradeOffer;
import com.uxplima.uxmessentials.trade.domain.TradeSession;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import org.junit.jupiter.api.Test;

/**
 * Pins the audit summary: {@link TradeReceipt#of} totals each side's item quantities (the sum of the offered stacks'
 * amounts, not the stack count) and carries each side's staked money and both participants for the audit line.
 */
class TradeReceiptTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");

    @Test
    void summarisesItemQuantitiesMoneyExperienceAndParticipants() {
        TradeSession session = TradeSession.open(TradeId.newId(), ALICE, BOB)
                .withOffer(
                        TradeSide.INITIATOR,
                        new TradeOffer(
                                List.of(new OfferedItem("diamond", 3), new OfferedItem("gold", 2)), Map.of(), 250L))
                .withOffer(TradeSide.PARTNER, new TradeOffer(List.of(), Map.of("coins", BigDecimal.valueOf(100))));

        TradeReceipt receipt = TradeReceipt.of(session);

        assertThat(receipt.initiator()).isEqualTo(ALICE);
        assertThat(receipt.partner()).isEqualTo(BOB);
        assertThat(receipt.initiatorItems()).isEqualTo(5);
        assertThat(receipt.partnerItems()).isZero();
        assertThat(receipt.initiatorMoney()).isEmpty();
        assertThat(receipt.partnerMoney()).containsExactly(entry("coins", BigDecimal.valueOf(100)));
        assertThat(receipt.initiatorExperience()).isEqualTo(250L);
        assertThat(receipt.partnerExperience()).isZero();
    }
}
