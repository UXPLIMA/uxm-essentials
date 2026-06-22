package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.fakes.CapturingSink;
import com.uxplima.uxmessentials.economy.fakes.Currencies;
import com.uxplima.uxmessentials.economy.fakes.InMemoryWorthOverrideStore;
import com.uxplima.uxmessentials.economy.fakes.KeyMessages;
import com.uxplima.uxmessentials.economy.fakes.RecordingAudit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /setworth}: write or clear a per-item worth override. A positive price upserts the override, audits
 * the set, and confirms it; a non-positive price falls through to a clear; clearing a set material removes it
 * and confirms; clearing an unset material reports nothing was set and audits nothing.
 */
class SetWorthTest {

    private InMemoryWorthOverrideStore store;
    private CapturingSink sink;
    private RecordingAudit audit;
    private SetWorth setWorth;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        store = new InMemoryWorthOverrideStore();
        sink = new CapturingSink();
        audit = new RecordingAudit();
        EconomyNotifier notifier = new EconomyNotifier(new KeyMessages(), sink);
        setWorth = new SetWorth(store, notifier, audit, Currencies.COINS, List.of(Currencies.COINS, Currencies.GEMS));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void positivePriceUpsertsAndAuditsAndConfirms() {
        setWorth.set(actor, "diamond", new BigDecimal("25"));

        assertThat(store.find("diamond")).contains(Worth.of(new BigDecimal("25"), "coins"));
        assertThat(sink.delivered("wallet.setworth-set")).isTrue();
        assertThat(audit.worthLines()).containsExactly(new RecordingAudit.WorthLine("economy_setworth", "diamond"));
    }

    @Test
    void positivePriceOverwritesAnExistingOverride() {
        setWorth.set(actor, "diamond", new BigDecimal("10"));
        setWorth.set(actor, "diamond", new BigDecimal("30"));

        assertThat(store.find("diamond")).contains(Worth.of(new BigDecimal("30"), "coins"));
    }

    @Test
    void aChosenCurrencyIsPersistedOnTheOverride() {
        setWorth.set(actor, "diamond", new BigDecimal("25"), "gems");

        assertThat(store.find("diamond")).contains(Worth.of(new BigDecimal("25"), "gems"));
        assertThat(sink.delivered("wallet.setworth-set")).isTrue();
    }

    @Test
    void anUnknownCurrencyFallsBackToTheDefault() {
        setWorth.set(actor, "diamond", new BigDecimal("25"), "doubloons");

        assertThat(store.find("diamond")).contains(Worth.of(new BigDecimal("25"), "coins"));
    }

    @Test
    void nonPositivePriceClearsTheOverride() {
        setWorth.set(actor, "diamond", new BigDecimal("10"));

        setWorth.set(actor, "diamond", BigDecimal.ZERO);

        assertThat(store.exists("diamond")).isFalse();
        assertThat(sink.delivered("wallet.setworth-cleared")).isTrue();
    }

    @Test
    void clearingASetMaterialRemovesItAndAudits() {
        setWorth.set(actor, "diamond", new BigDecimal("10"));
        audit.worthLines();

        setWorth.clear(actor, "diamond");

        assertThat(store.exists("diamond")).isFalse();
        assertThat(sink.delivered("wallet.setworth-cleared")).isTrue();
        assertThat(audit.worthLines()).contains(new RecordingAudit.WorthLine("economy_setworth_clear", "diamond"));
    }

    @Test
    void clearingAnUnsetMaterialReportsNothingWasSet() {
        setWorth.clear(actor, "emerald");

        assertThat(sink.delivered("wallet.setworth-not-set")).isTrue();
        assertThat(audit.worthLines()).isEmpty();
    }
}
