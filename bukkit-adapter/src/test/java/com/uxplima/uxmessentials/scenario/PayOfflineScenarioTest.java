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

/** Paying a player who is not online: the money arrives at once, and the payee is told once, as they next join. */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PayOfflineScenarioTest {

    private RunningPlugin running;
    private ScenarioPlayer payer;

    @BeforeAll
    void start() {
        running = RunningPlugin.start();
        payer = running.join("Paula");
        payer.setOp(true);
        running.run(payer, "eco give Paula 1000");
    }

    @AfterAll
    void stop() {
        running.close();
    }

    @Test
    void aPlayerWhoIsOfflineIsPaidAtOnceAndToldOnceAsTheyNextJoin() {
        UUID pete = joinedAndLeft("Pete");
        BigDecimal before = balance(payer.getUniqueId());

        running.run(payer, "pay Pete 25");

        assertThat(balance(pete)).isEqualByComparingTo("25");
        assertThat(balance(payer.getUniqueId())).isEqualByComparingTo(before.subtract(new BigDecimal("25")));
        ScenarioPlayer back = running.join("Pete");
        assertThat(RunningPlugin.said(back)).anySatisfy(line -> assertThat(line).contains("Paula", "25"));

        running.quit(back);
        ScenarioPlayer again = running.join("Pete");
        assertThat(RunningPlugin.said(again)).as("told once").noneMatch(line -> line.contains("Paula"));
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aBedrockPlayerWhoIsOfflineIsPaidAndToldAsTheyNextJoin() {
        UUID bea = joinedAndLeft("#Bea");

        running.run(payer, "pay #Bea 10");

        assertThat(balance(bea)).isEqualByComparingTo("10");
        ScenarioPlayer back = running.join("#Bea");
        assertThat(RunningPlugin.said(back)).anySatisfy(line -> assertThat(line).contains("Paula", "10"));
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aPlayerOnlineHereIsToldAtOnce() {
        ScenarioPlayer omar = running.join("Omar");
        RunningPlugin.said(omar);

        running.run(payer, "pay Omar 5");

        assertThat(RunningPlugin.said(omar)).anySatisfy(line -> assertThat(line).contains("Paula", "5"));
        assertThat(running.problems()).isEmpty();
    }

    private UUID joinedAndLeft(String name) {
        ScenarioPlayer player = running.join(name);
        running.quit(player);
        return player.getUniqueId();
    }

    private BigDecimal balance(UUID player) {
        UxmEconomyQuery economy = running.api().economy().orElseThrow();
        return running.await(economy.balance(player)).amount();
    }
}
