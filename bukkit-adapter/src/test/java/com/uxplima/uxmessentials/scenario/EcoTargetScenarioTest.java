package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import com.uxplima.uxmessentials.api.query.UxmEconomyQuery;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** {@code /eco give|take|set} by name: a player who logged off is paid, and a name nobody holds moves nothing. */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EcoTargetScenarioTest {

    private RunningPlugin running;

    @BeforeAll
    void start() {
        running = RunningPlugin.start();
    }

    @AfterAll
    void stop() {
        running.close();
    }

    @Test
    void staffPayAndChargeAPlayerWhoLoggedOff() {
        ScenarioPlayer staff = running.join("Esme");
        staff.setOp(true);
        ScenarioPlayer away = running.join("Odile");
        UUID odile = away.getUniqueId();
        running.quit(away);
        BigDecimal before = balance(odile);

        running.run(staff, "eco give Odile 40");
        assertThat(balance(odile)).isEqualByComparingTo(before.add(new BigDecimal("40")));

        running.run(staff, "eco take Odile 15");
        assertThat(balance(odile)).isEqualByComparingTo(before.add(new BigDecimal("25")));

        running.run(staff, "eco set Odile 7");
        assertThat(balance(odile)).isEqualByComparingTo("7");
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aNameNobodyHoldsIsAnsweredAndMovesNothing() {
        ScenarioPlayer staff = running.join("Ezra");
        staff.setOp(true);
        RunningPlugin.said(staff);

        running.run(staff, "eco give Nobody 40");

        assertThat(RunningPlugin.said(staff)).singleElement().asString().contains("Nobody");
        assertThat(running.problems()).isEmpty();
    }

    private BigDecimal balance(UUID player) {
        UxmEconomyQuery economy = running.api().economy().orElseThrow();
        return running.await(economy.balance(player)).amount();
    }
}
