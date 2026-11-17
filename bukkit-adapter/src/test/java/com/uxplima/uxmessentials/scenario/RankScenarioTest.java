package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.api.query.UxmRanksQuery;
import com.uxplima.uxmessentials.api.view.UxmRankStanding;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** An operator setting ranks, and the actions a rank runs, for players who are not plain Java players online here. */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RankScenarioTest {

    /** A free ladder whose second rung names the player both ways. */
    private static final String LADDER = """
            first {
              order = 10
              display-name = "First"
              cost = 0
              requirements = []
              actions = []
            }
            second {
              order = 20
              display-name = "Second"
              cost = 0
              requirements = []
              actions = [
                "message account {uuid}"
                "message name {player}"
              ]
            }
            """;

    private RunningPlugin running;

    @BeforeAll
    void start() {
        running = RunningPlugin.start(Map.of("modules/ranks/ranks.conf", LADDER));
    }

    @AfterAll
    void stop() {
        running.close();
    }

    @Test
    void theConsoleSetsTheRankOfAPlayerWhoIsOffline() {
        ScenarioPlayer player = running.join("Olive");
        running.quit(player);

        running.console("setrank Olive second");

        ScenarioPlayer back = running.join("Olive");
        Optional<UxmRankStanding> standing = running.await(ranks().standingOf(back.getUniqueId()));
        assertThat(standing).map(found -> found.rank().id()).contains("second");
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aSelectorStillPicksThePlayerOnlineHere() {
        ScenarioPlayer player = running.join("Selma");
        player.setOp(true);

        running.run(player, "setrank @s second");

        Optional<UxmRankStanding> standing = running.await(ranks().standingOf(player.getUniqueId()));
        assertThat(standing).map(found -> found.rank().id()).contains("second");
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aBedrockPlayerWithAPrefixTheAccountNameCheckDoesNotKnowGetsEveryLine() {
        ScenarioPlayer player = running.join("#Bea");
        RunningPlugin.said(player);

        running.run(player, "rankup");

        assertThat(RunningPlugin.said(player)).contains("account " + player.getUniqueId(), "name #Bea");
        assertThat(running.problems()).isEmpty();
    }

    private UxmRanksQuery ranks() {
        return running.api().ranks().orElseThrow();
    }
}
