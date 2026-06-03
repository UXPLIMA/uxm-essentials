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
        WorthTable config = new WorthTable(Map.of("diamond", new BigDecimal("10")));
        CombiningWorthSource worth = new CombiningWorthSource(overrides, config);

        assertThat(worth.unitPrice("diamond")).contains(new BigDecimal("25"));
    }

    @Test
    void fallsBackToConfigWhenNoOverride() {
        CombiningWorthSource worth = new CombiningWorthSource(
                new InMemoryWorthOverrideStore(), new WorthTable(Map.of("diamond", new BigDecimal("10"))));

        assertThat(worth.unitPrice("diamond")).contains(new BigDecimal("10"));
    }

    @Test
    void unpricedInBothSourcesIsEmpty() {
        CombiningWorthSource worth = new CombiningWorthSource(new InMemoryWorthOverrideStore(), WorthTable.empty());

        assertThat(worth.unitPrice("emerald")).isEmpty();
    }

    @Test
    void stackValueScalesTheResolvedUnitPrice() {
        InMemoryWorthOverrideStore overrides = new InMemoryWorthOverrideStore();
        overrides.set("diamond", new BigDecimal("25"));
        CombiningWorthSource worth = new CombiningWorthSource(overrides, WorthTable.empty());

        assertThat(worth.stackValue("diamond", 4)).contains(new BigDecimal("100"));
    }
}
