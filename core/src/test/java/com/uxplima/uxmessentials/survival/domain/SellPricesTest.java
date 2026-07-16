package com.uxplima.uxmessentials.survival.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Pins the pure autosell price maths: per-item lookup, stack multiplication, unsellable fall-through, and validation. */
class SellPricesTest {

    private final SellPrices prices = new SellPrices(Map.of(
            "DIAMOND", new BigDecimal("300"),
            "IRON_INGOT", new BigDecimal("5")));

    @Test
    void aPricedMaterialSellsPerItem() {
        assertThat(prices.priceOf("DIAMOND")).contains(new BigDecimal("300"));
    }

    @Test
    void saleValueMultipliesByTheStackAmount() {
        assertThat(prices.saleValue("IRON_INGOT", 64)).contains(new BigDecimal("320"));
    }

    @Test
    void lookupIsCaseInsensitive() {
        assertThat(new SellPrices(Map.of("gold_ingot", new BigDecimal("8"))).priceOf("GOLD_INGOT"))
                .contains(new BigDecimal("8"));
    }

    @Test
    void anUnpricedMaterialIsNotSellable() {
        assertThat(prices.priceOf("COBBLESTONE")).isEmpty();
        assertThat(prices.saleValue("COBBLESTONE", 10)).isEmpty();
    }

    @Test
    void aNegativePriceIsRejected() {
        assertThatThrownBy(() -> new SellPrices(Map.of("DIAMOND", new BigDecimal("-1"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
