package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.fakes.InMemoryWorthOverrideStore;
import org.junit.jupiter.api.Test;

/**
 * The combining worth source consults the {@code /setworth} override store first, then the config
 * {@link WorthTable}, then whatever fallback source it was given. So an override beats the config price, an
 * untouched material keeps its configured worth, a material priced only by the fallback still sells, and a
 * material in none of the three stays not-sellable.
 */
class CombiningWorthSourceTest {

    @Test
    void overrideBeatsConfig() {
        InMemoryWorthOverrideStore overrides = new InMemoryWorthOverrideStore();
        overrides.set("diamond", new BigDecimal("25"), "coins");
        WorthTable config = new WorthTable(Map.of("diamond", Worth.of(new BigDecimal("10"), "coins")));
        CombiningWorthSource worth = new CombiningWorthSource(overrides, config);

        assertThat(worth.unitPrice("diamond")).contains(Worth.of(new BigDecimal("25"), "coins"));
    }

    @Test
    void overrideKeepsItsOwnCurrency() {
        InMemoryWorthOverrideStore overrides = new InMemoryWorthOverrideStore();
        overrides.set("diamond", new BigDecimal("25"), "gems");
        WorthTable config = new WorthTable(Map.of("diamond", Worth.of(new BigDecimal("10"), "coins")));
        CombiningWorthSource worth = new CombiningWorthSource(overrides, config);

        assertThat(worth.unitPrice("diamond")).contains(Worth.of(new BigDecimal("25"), "gems"));
    }

    @Test
    void fallsBackToConfigWhenNoOverride() {
        CombiningWorthSource worth = new CombiningWorthSource(
                new InMemoryWorthOverrideStore(),
                new WorthTable(Map.of("diamond", Worth.of(new BigDecimal("10"), "coins"))));

        assertThat(worth.unitPrice("diamond")).contains(Worth.of(new BigDecimal("10"), "coins"));
    }

    @Test
    void unpricedInBothSourcesIsEmpty() {
        CombiningWorthSource worth = new CombiningWorthSource(new InMemoryWorthOverrideStore(), WorthTable.empty());

        assertThat(worth.unitPrice("emerald")).isEmpty();
    }

    @Test
    void configBeatsTheFallback() {
        // The fallback is where a foreign shop plugin's catalogue enters, and an operator's own configured price
        // must always win over it.
        CombiningWorthSource worth = new CombiningWorthSource(
                new InMemoryWorthOverrideStore(),
                new WorthTable(Map.of("diamond", Worth.of(new BigDecimal("10"), "coins"))),
                material -> Optional.of(Worth.of(new BigDecimal("3"), "coins")));

        assertThat(worth.unitPrice("diamond")).contains(Worth.of(new BigDecimal("10"), "coins"));
    }

    @Test
    void overrideBeatsTheFallbackToo() {
        InMemoryWorthOverrideStore overrides = new InMemoryWorthOverrideStore();
        overrides.set("diamond", new BigDecimal("25"), "coins");
        CombiningWorthSource worth = new CombiningWorthSource(
                overrides, WorthTable.empty(), material -> Optional.of(Worth.of(new BigDecimal("3"), "coins")));

        assertThat(worth.unitPrice("diamond")).contains(Worth.of(new BigDecimal("25"), "coins"));
    }

    @Test
    void anItemPricedOnlyByTheFallbackIsStillSellable() {
        CombiningWorthSource worth = new CombiningWorthSource(
                new InMemoryWorthOverrideStore(),
                WorthTable.empty(),
                material -> material.equals("emerald")
                        ? Optional.of(Worth.of(new BigDecimal("7"), "coins"))
                        : Optional.empty());

        assertThat(worth.unitPrice("emerald")).contains(Worth.of(new BigDecimal("7"), "coins"));
        assertThat(worth.unitPrice("dirt"))
                .as("the fallback prices nothing else")
                .isEmpty();
    }

    @Test
    void stackValueScalesTheResolvedUnitPrice() {
        InMemoryWorthOverrideStore overrides = new InMemoryWorthOverrideStore();
        overrides.set("diamond", new BigDecimal("25"), "coins");
        CombiningWorthSource worth = new CombiningWorthSource(overrides, WorthTable.empty());

        assertThat(worth.stackValue("diamond", 4)).contains(Worth.of(new BigDecimal("100"), "coins"));
    }
}
