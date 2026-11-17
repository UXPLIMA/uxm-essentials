package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * A command that takes a player reaches every name a player can have: a Bedrock player whose Floodgate prefix the
 * vanilla parser refuses, a name a proxy forwards longer than sixteen characters, and a selector. A name nobody holds
 * is answered in the plugin's own words, never with the server's parse error.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlayerArgumentScenarioTest {

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
    void aFloodgateNameALongNameAndASelectorAreEachReached() {
        ScenarioPlayer staff = running.join("Stella");
        staff.setOp(true);
        ScenarioPlayer bedrock = running.join("*BedrockGamer123");
        ScenarioPlayer forwarded = running.join("a_name_longer_than_sixteen");
        bedrock.setFoodLevel(2);
        forwarded.setFoodLevel(2);

        running.run(staff, "feed *bedrockgamer123");
        running.run(staff, "feed a_name_longer_than_sixteen");

        assertThat(bedrock.getFoodLevel()).isEqualTo(20);
        assertThat(forwarded.getFoodLevel()).isEqualTo(20);

        bedrock.setFoodLevel(2);
        forwarded.setFoodLevel(2);
        running.run(staff, "feed @a");

        assertThat(List.of(bedrock.getFoodLevel(), forwarded.getFoodLevel())).containsOnly(20);
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aNameNobodyHoldsIsAnsweredInThePluginsWords() {
        ScenarioPlayer staff = running.join("Sage");
        staff.setOp(true);
        RunningPlugin.said(staff);

        running.run(staff, "sudo *Nobody say hi");

        assertThat(RunningPlugin.said(staff))
                .singleElement()
                .asString()
                .contains("*Nobody")
                .doesNotContainIgnoringCase("no player was found");
        assertThat(running.problems()).isEmpty();
    }
}
