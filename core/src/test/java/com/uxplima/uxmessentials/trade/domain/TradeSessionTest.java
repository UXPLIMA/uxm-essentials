package com.uxplima.uxmessentials.trade.domain;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** A trade arms only on two confirmations of the offers on the table, and any change to an offer disarms it. */
class TradeSessionTest {

    private static final TradeOffer DIAMONDS = new TradeOffer(List.of(new OfferedItem("diamond", 3)), Map.of());
    private static final TradeOffer COINS = new TradeOffer(List.of(), Map.of("coins", new BigDecimal("50")));

    private TradeSession staked() {
        return TradeSession.open(TradeId.newId(), ALEX, SAM)
                .withOffer(TradeSide.INITIATOR, DIAMONDS)
                .withOffer(TradeSide.PARTNER, COINS);
    }

    @Test
    void bothSidesConfirmingArmsTheTradeAndOnlyAnArmedTradeCommits() {
        TradeSession session = staked().confirm(TradeSide.INITIATOR);

        assertThatThrownBy(session::ready).isInstanceOf(IllegalTradeTransitionException.class);
        assertThatThrownBy(session::commit).isInstanceOf(IllegalTradeTransitionException.class);

        TradeSession committed = session.confirm(TradeSide.PARTNER).ready().commit();

        assertThat(committed.state()).isEqualTo(TradeState.COMMITTED);
        assertThat(committed.offer(TradeSide.INITIATOR)).isEqualTo(DIAMONDS);
        assertThat(committed.offer(TradeSide.PARTNER)).isEqualTo(COINS);
    }

    @Test
    void changingAnOfferAfterBothConfirmedDisarmsTheTradeAndClearsBothConfirmations() {
        TradeSession armed =
                staked().confirm(TradeSide.INITIATOR).confirm(TradeSide.PARTNER).ready();

        TradeOffer fewer = new TradeOffer(List.of(new OfferedItem("diamond", 1)), Map.of());
        TradeSession swapped = armed.withOffer(TradeSide.INITIATOR, fewer);

        assertThat(swapped.state()).isEqualTo(TradeState.OPEN);
        assertThat(swapped.confirmed(TradeSide.INITIATOR)).isFalse();
        assertThat(swapped.confirmed(TradeSide.PARTNER)).isFalse();
        assertThatThrownBy(swapped::commit).isInstanceOf(IllegalTradeTransitionException.class);
    }

    @Test
    void withdrawingAConfirmationDropsAnArmedTradeBackToOpen() {
        TradeSession armed =
                staked().confirm(TradeSide.INITIATOR).confirm(TradeSide.PARTNER).ready();

        TradeSession withdrawn = armed.unconfirm(TradeSide.PARTNER);

        assertThat(withdrawn.state()).isEqualTo(TradeState.OPEN);
        assertThat(withdrawn.bothConfirmed()).isFalse();
        assertThatThrownBy(withdrawn::commit).isInstanceOf(IllegalTradeTransitionException.class);
    }

    @Test
    void aFinishedTradeTakesNoFurtherChange() {
        TradeSession committed = staked().confirm(TradeSide.INITIATOR)
                .confirm(TradeSide.PARTNER)
                .ready()
                .commit();
        TradeSession cancelled = staked().cancel();

        for (TradeSession finished : List.of(committed, cancelled)) {
            assertThatThrownBy(() -> finished.withOffer(TradeSide.INITIATOR, TradeOffer.empty()))
                    .isInstanceOf(IllegalTradeTransitionException.class);
            assertThatThrownBy(finished::cancel).isInstanceOf(IllegalTradeTransitionException.class);
            assertThatThrownBy(() -> finished.unconfirm(TradeSide.PARTNER))
                    .isInstanceOf(IllegalTradeTransitionException.class);
        }
        assertThatThrownBy(committed::commit).isInstanceOf(IllegalTradeTransitionException.class);
    }

    @Test
    void aPlayerCannotTradeWithThemselves() {
        assertThatThrownBy(() -> TradeSession.open(TradeId.newId(), ALEX, ALEX))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anOfferCannotStakeANegativeAmountOrNegativeExperience() {
        assertThatThrownBy(() -> new TradeOffer(List.of(), Map.of("coins", new BigDecimal("-1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TradeOffer(List.of(), Map.of(), -5L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OfferedItem("dirt", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
