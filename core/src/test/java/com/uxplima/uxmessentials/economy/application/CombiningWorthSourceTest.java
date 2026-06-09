package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import com.uxplima.uxmessentials.economy.fakes.InMemoryWorthOverrideStore;
import org.junit.jupiter.api.Test;

/**
 * The combining worth source consults the {@code /setworth} override store first, falling back to the config
 * {@link WorthTable} only when a material has no override. So an override beats the config price, an untouched
 * material keeps its configured worth, and a material in neither source stays not-sellable.
 */
class CombiningWorthSourceTest {

    @Test
    void overrideBeatsConfig() {
        InMemoryWorthOverrideStore overrides = new InMemoryWorthOverrideStore();
        overrides.set("diamond", new BigDecimal("25"));
        WorthTable config = new WorthTable(Map.of("diamond", Worth.of(new BigDecimal("10"), "coins")));
        CombiningWorthSource worth = new CombiningWorthSource(overrides, config, "coins");

        assertThat(worth.unitPrice("diamond")).contains(Worth.of(new BigDecimal("25"), "coins"));
    }

    @Test
    void fallsBackToConfigWhenNoOverride() {
        CombiningWorthSource worth = new CombiningWorthSource(
                new InMemoryWorthOverrideStore(),
                new WorthTable(Map.of("diamond", Worth.of(new BigDecimal("10"), "coins"))),
                "coins");

        assertThat(worth.unitPrice("diamond")).contains(Worth.of(new BigDecimal("10"), "coins"));
    }

    @Test
    void unpricedInBothSourcesIsEmpty() {
        CombiningWorthSource worth =
                new CombiningWorthSource(new InMemoryWorthOverrideStore(), WorthTable.empty(), "coins");

        assertThat(worth.unitPrice("emerald")).isEmpty();
    }

    @Test
    void stackValueScalesTheResolvedUnitPrice() {
        InMemoryWorthOverrideStore overrides = new InMemoryWorthOverrideStore();
        overrides.set("diamond", new BigDecimal("25"));
        CombiningWorthSource worth = new CombiningWorthSource(overrides, WorthTable.empty(), "coins");

        assertThat(worth.stackValue("diamond", 4)).contains(Worth.of(new BigDecimal("100"), "coins"));
    }
}
