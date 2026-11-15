package com.uxplima.uxmessentials.trade.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.port.TradeExperience;
import com.uxplima.uxmessentials.trade.domain.OfferedItem;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import com.uxplima.uxmessentials.trade.domain.TradeOffer;
import com.uxplima.uxmessentials.trade.domain.TradeSession;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import org.junit.jupiter.api.Test;

/** A committed trade moves every staked coin and experience point, or, when any leg fails, none of them. */
class TradeSettlementTest {

    private final TradeWallets wallets = new TradeWallets();
    private final Experience experience = new Experience();
    private final TradeSettlement settlement = new TradeSettlement(wallets, experience);

    private static TradeSession trade(TradeOffer alexGives, TradeOffer samGives) {
        return TradeSession.open(TradeId.newId(), ALEX, SAM)
                .withOffer(TradeSide.INITIATOR, alexGives)
                .withOffer(TradeSide.PARTNER, samGives);
    }

    private static TradeOffer money(String currency, String amount) {
        return new TradeOffer(List.of(), Map.of(currency, new BigDecimal(amount)));
    }

    @Test
    void bothSidesMoneyAndExperienceChangeHands() {
        wallets.set(ALEX, "coins", "100").set(SAM, "gems", "10");
        experience.set(ALEX, 30).set(SAM, 5);
        TradeOffer alexGives = new TradeOffer(List.of(), Map.of("coins", new BigDecimal("40")), 30);
        TradeOffer samGives = new TradeOffer(List.of(), Map.of("gems", new BigDecimal("10")), 0);

        assertThat(settlement.settle(trade(alexGives, samGives))).isTrue();

        assertThat(wallets.balance(ALEX, "coins")).isEqualByComparingTo("60");
        assertThat(wallets.balance(SAM, "coins")).isEqualByComparingTo("40");
        assertThat(wallets.balance(ALEX, "gems")).isEqualByComparingTo("10");
        assertThat(wallets.balance(SAM, "gems")).isEqualByComparingTo("0");
        assertThat(experience.points(ALEX)).isZero();
        assertThat(experience.points(SAM)).isEqualTo(35);
    }

    @Test
    void anItemOnlyTradeMovesNoMoneyAndSettles() {
        TradeOffer items = new TradeOffer(List.of(new OfferedItem("diamond", 2)), Map.of());

        assertThat(settlement.settle(trade(items, TradeOffer.empty()))).isTrue();

        assertThat(TradeSettlement.transfers(trade(items, TradeOffer.empty()))).isEmpty();
        assertThat(experience.moves).isZero();
    }

    @Test
    void aPayerWhoCannotCoverTheirMoneyMovesNothingAndGetsTheirExperienceBack() {
        wallets.set(ALEX, "coins", "100").set(SAM, "gems", "3");
        experience.set(ALEX, 20);
        TradeOffer alexGives = new TradeOffer(List.of(), Map.of("coins", new BigDecimal("40")), 20);

        assertThat(settlement.settle(trade(alexGives, money("gems", "10")))).isFalse();

        assertThat(wallets.balance(ALEX, "coins")).isEqualByComparingTo("100");
        assertThat(wallets.balance(SAM, "gems")).isEqualByComparingTo("3");
        assertThat(experience.points(ALEX)).isEqualTo(20);
        assertThat(experience.points(SAM)).isZero();
    }

    @Test
    void aLegRefusedAfterAnotherMovedReversesTheOneThatMoved() {
        wallets.set(ALEX, "coins", "100").set(SAM, "gems", "10");
        // The probe passes but the move itself is refused, so the coin leg has already moved when gems fail.
        wallets.refuse("gems");

        assertThat(settlement.settle(trade(money("coins", "40"), money("gems", "10"))))
                .isFalse();

        assertThat(wallets.balance(ALEX, "coins")).isEqualByComparingTo("100");
        assertThat(wallets.balance(SAM, "coins")).isEqualByComparingTo("0");
        assertThat(wallets.balance(SAM, "gems")).isEqualByComparingTo("10");
        assertThat(wallets.total("coins")).isEqualByComparingTo("100");
    }

    @Test
    void experienceTheSecondSideCannotCoverRefundsTheFirstSidesExperienceAndTouchesNoMoney() {
        wallets.set(ALEX, "coins", "100");
        experience.set(ALEX, 15).set(SAM, 2);
        TradeOffer alexGives = new TradeOffer(List.of(), Map.of("coins", new BigDecimal("40")), 15);
        TradeOffer samGives = new TradeOffer(List.of(), Map.of(), 9);

        assertThat(settlement.settle(trade(alexGives, samGives))).isFalse();

        assertThat(experience.points(ALEX)).isEqualTo(15);
        assertThat(experience.points(SAM)).isEqualTo(2);
        assertThat(wallets.balance(ALEX, "coins")).isEqualByComparingTo("100");
        assertThat(wallets.balance(SAM, "coins")).isEqualByComparingTo("0");
    }

    @Test
    void bothSidesStakingTheSameCurrencyEachPayTheirOwnLeg() {
        wallets.set(ALEX, "coins", "100").set(SAM, "coins", "30");

        assertThat(settlement.settle(trade(money("coins", "70"), money("coins", "30"))))
                .isTrue();

        assertThat(wallets.balance(ALEX, "coins")).isEqualByComparingTo("60");
        assertThat(wallets.balance(SAM, "coins")).isEqualByComparingTo("70");
        assertThat(wallets.total("coins")).isEqualByComparingTo("130");
    }

    @Test
    void aZeroEntryIsNotALeg() {
        TradeSession session = trade(money("coins", "0"), money("gems", "5"));

        assertThat(TradeSettlement.transfers(session)).singleElement().satisfies(leg -> {
            assertThat(leg.payer()).isEqualTo(TradeSide.PARTNER);
            assertThat(leg.currencyId()).isEqualTo("gems");
        });
    }

    @Test
    void theReceiptCountsEachSidesItemsAndStakes() {
        TradeOffer alexGives = new TradeOffer(
                List.of(new OfferedItem("diamond", 3), new OfferedItem("emerald", 2)),
                Map.of("coins", new BigDecimal("5")),
                7);

        TradeReceipt receipt = TradeReceipt.of(trade(alexGives, money("gems", "1")));

        assertThat(receipt.initiatorItems()).isEqualTo(5);
        assertThat(receipt.partnerItems()).isZero();
        assertThat(receipt.initiatorExperience()).isEqualTo(7);
        assertThat(receipt.partnerMoney()).containsOnlyKeys("gems");
    }

    /** Experience on online players; a withdraw the holder cannot cover removes nothing. */
    private static final class Experience implements TradeExperience {

        private final Map<UUID, Long> points = new HashMap<>();
        private int moves;

        Experience set(PlayerRef who, long amount) {
            points.put(who.uuid(), amount);
            return this;
        }

        long points(PlayerRef who) {
            return points.getOrDefault(who.uuid(), 0L);
        }

        @Override
        public long available(PlayerRef who) {
            return points(who);
        }

        @Override
        public boolean withdraw(PlayerRef who, long amount) {
            if (points(who) < amount) {
                return false;
            }
            moves++;
            points.put(who.uuid(), points(who) - amount);
            return true;
        }

        @Override
        public void deposit(PlayerRef who, long amount) {
            moves++;
            points.put(who.uuid(), points(who) + amount);
        }
    }
}
