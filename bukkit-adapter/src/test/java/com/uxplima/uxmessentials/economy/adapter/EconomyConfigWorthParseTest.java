package com.uxplima.uxmessentials.economy.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.economy.application.Worth;
import com.uxplima.uxmessentials.economy.application.WorthTable;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/**
 * {@link EconomyConfig#worthTable} parses each {@code worth.items} entry into a {@link Worth}. A two-field
 * {@code "MATERIAL:price"} entry prices the item in the default currency; a three-field
 * {@code "MATERIAL:price:currencyId"} entry prices it in that currency; an entry naming a currency that is not
 * configured falls back to the default and is warned about rather than dropped.
 */
class EconomyConfigWorthParseTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).symbol("$").precision(2).build();
    private static final Currency GEMS =
            Currency.builder(CurrencyId.of("gems")).symbol("♦").precision(0).build();
    private static final CurrencyRegistry REGISTRY = CurrencyRegistry.of(List.of(COINS, GEMS), CurrencyId.of("coins"));

    @Test
    void twoFieldEntryPricesInTheDefaultCurrency() {
        WorthTable table = parse("DIAMOND:50");

        assertThat(table.unitPrice("diamond")).contains(Worth.of(new java.math.BigDecimal("50"), "coins"));
    }

    @Test
    void threeFieldEntryPricesInTheNamedCurrency() {
        WorthTable table = parse("DIAMOND:50:gems");

        assertThat(table.unitPrice("diamond")).contains(Worth.of(new java.math.BigDecimal("50"), "gems"));
    }

    @Test
    void anUnknownCurrencyFallsBackToTheDefaultAndWarns() {
        RecordingLogger log = new RecordingLogger();

        WorthTable table = parse(log, "DIAMOND:50:doubloons");

        assertThat(table.unitPrice("diamond")).contains(Worth.of(new java.math.BigDecimal("50"), "coins"));
        assertThat(log.warnings).hasSize(1);
    }

    @Test
    void malformedEntriesAreSkippedNotFatal() {
        WorthTable table = parse("DIAMOND:notanumber", "EMERALD:40");

        assertThat(table.unitPrice("diamond")).isEmpty();
        assertThat(table.unitPrice("emerald")).contains(Worth.of(new java.math.BigDecimal("40"), "coins"));
    }

    private WorthTable parse(String... items) {
        return parse(new RecordingLogger(), items);
    }

    private WorthTable parse(Logger log, String... items) {
        ConfigStore config = new WorthItemsConfig(List.of(items));
        return new EconomyConfig(config).worthTable(REGISTRY, log);
    }

    /** A config that serves only the {@code worth.items} list; every other path returns the caller's fallback. */
    private static final class WorthItemsConfig implements ConfigStore {
        private final List<String> items;

        WorthItemsConfig(List<String> items) {
            this.items = List.copyOf(items);
        }

        @Override
        public List<String> getStringList(String path, List<String> fallback) {
            return "worth.items".equals(path) ? items : List.copyOf(fallback);
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    /** Captures warn lines so a test can assert the unknown-currency path logs rather than drops the entry. */
    private static final class RecordingLogger implements Logger {
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
