package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The worth lookup table is a case-insensitive material-id → unit-price map. A price is per single item, so a
 * stack value is the unit price times the amount; an unpriced material is "not sellable" and yields no price.
 */
class WorthTableTest {

    @Test
    void unknownMaterialHasNoPrice() {
        WorthTable table = new WorthTable(Map.of("diamond", Worth.of(new BigDecimal("10"), "coins")));
        assertThat(table.unitPrice("emerald")).isEmpty();
    }

    @Test
    void knownMaterialResolvesItsUnitPrice() {
        WorthTable table = new WorthTable(Map.of("diamond", Worth.of(new BigDecimal("10"), "coins")));
        assertThat(table.unitPrice("diamond")).contains(Worth.of(new BigDecimal("10"), "coins"));
    }

    @Test
    void lookupIsCaseInsensitive() {
        WorthTable table = new WorthTable(Map.of("diamond", Worth.of(new BigDecimal("10"), "coins")));
        assertThat(table.unitPrice("DIAMOND")).contains(Worth.of(new BigDecimal("10"), "coins"));
    }

    @Test
    void stackValueScalesByAmount() {
        WorthTable table = new WorthTable(Map.of("diamond", Worth.of(new BigDecimal("10"), "coins")));
        assertThat(table.stackValue("diamond", 4)).contains(Worth.of(new BigDecimal("40"), "coins"));
    }
}
