package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.bukkit.Material;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** A borrower repaying through the /loan dashboard. */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoanScenarioTest {

    /** Prompts read the next chat line, so a scenario can answer one. */
    private static final String TEXT_INPUT = """
            default-mode = chat
            cancel-keywords = ["cancel"]
            """;

    private static final int FIRST_LOAN = 10;

    private RunningPlugin running;

    @BeforeAll
    void start() {
        running = RunningPlugin.start(Map.of("text-input.conf", TEXT_INPUT));
    }

    @AfterAll
    void stop() {
        running.close();
    }

    @Test
    void aCustomAmountIsRepaidWithLeftClicksOnly() {
        ScenarioPlayer borrower = running.join(
                "Lena", joining -> joining.addAttachment(running.plugin, "uxmessentials.economy.loan", true));
        running.run(borrower, "loan take 100");
        running.run(borrower, "loan");
        assertThat(running.slotOf(borrower, Material.BOOK)).as("the loan").isEqualTo(FIRST_LOAN);

        running.click(borrower, running.slotOf(borrower, Material.GOLD_NUGGET));
        running.click(borrower, FIRST_LOAN);
        RunningPlugin.said(borrower);
        running.chat(borrower, "7");

        assertThat(RunningPlugin.said(borrower)).anyMatch(line -> line.endsWith("7.00 coins."));
        assertThat(running.problems()).isEmpty();
    }
}
