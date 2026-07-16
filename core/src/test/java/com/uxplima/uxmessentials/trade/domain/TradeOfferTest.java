package com.uxplima.uxmessentials.trade.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Pins the {@link TradeOffer} and {@link OfferedItem} value objects: emptiness, defensive copies, and validation. */
class TradeOfferTest {

    @Test
    void emptyOfferHasNoItemsOrMoney() {
        assertThat(TradeOffer.empty().isEmpty()).isTrue();
        assertThat(TradeOffer.empty().items()).isEmpty();
        assertThat(TradeOffer.empty().money()).isEmpty();
    }

    @Test
    void anOfferWithItemsOrMoneyIsNotEmpty() {
        assertThat(new TradeOffer(List.of(new OfferedItem("diamond", 2)), Map.of()).isEmpty())
                .isFalse();
        assertThat(new TradeOffer(List.of(), Map.of("coins", new BigDecimal("5"))).isEmpty())
                .isFalse();
    }

    @Test
    void componentsAreDefensivelyCopied() {
        List<OfferedItem> items = new java.util.ArrayList<>(List.of(new OfferedItem("stone", 1)));
        TradeOffer offer = new TradeOffer(items, Map.of());

        items.add(new OfferedItem("dirt", 1));

        assertThat(offer.items()).hasSize(1);
    }

    @Test
    void negativeMoneyIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TradeOffer(List.of(), Map.of("coins", new BigDecimal("-1"))));
    }

    @Test
    void blankCurrencyIdIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TradeOffer(List.of(), Map.of("  ", new BigDecimal("1"))));
    }

    @Test
    void offeredItemRejectsBlankHandleAndNonPositiveAmount() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OfferedItem(" ", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new OfferedItem("diamond", 0));
    }
}
