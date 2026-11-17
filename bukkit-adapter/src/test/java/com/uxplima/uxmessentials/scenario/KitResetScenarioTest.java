package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** An operator resetting a one-time kit for a player who is not online, who may claim it again when they come back. */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KitResetScenarioTest {

    /** A one-time kit whose claim says so, so a claim can be told from a refusal. */
    private static final String STARTER = """
            one-time = true
            items = []
            claim-actions = [ { type = "message", value = "starter handed out" } ]
            """;

    private static final String HANDED_OUT = "starter handed out";

    private RunningPlugin running;

    @BeforeAll
    void start() {
        running = RunningPlugin.start(Map.of("modules/kits/kits/starter.conf", STARTER));
    }

    @AfterAll
    void stop() {
        running.close();
    }

    @Test
    void theConsoleResetsTheKitOfAPlayerWhoIsOffline() {
        ScenarioPlayer away = claimedThenLeft("Olive");

        running.console("kit reset Olive starter");

        assertThat(claimsAgain(away)).isTrue();
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void theConsoleResetsEveryKitOfAPlayerWhoIsOffline() {
        ScenarioPlayer away = claimedThenLeft("Oscar");

        running.console("kit reset Oscar");

        assertThat(claimsAgain(away)).isTrue();
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aClaimStaysClaimedAcrossARejoinWithoutAReset() {
        ScenarioPlayer away = claimedThenLeft("Opal");

        assertThat(claimsAgain(away)).isFalse();
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aSelectorStillResetsThePlayerOnlineHere() {
        ScenarioPlayer player = running.join("Selma");
        player.setOp(true);
        running.run(player, "kit starter");
        RunningPlugin.said(player);

        running.run(player, "kit reset @s starter");
        running.run(player, "kit starter");

        assertThat(RunningPlugin.said(player)).anyMatch(line -> line.contains(HANDED_OUT));
        assertThat(running.problems()).isEmpty();
    }

    /** {@code name} joins, claims the kit, finds it refused a second time, and leaves. */
    private ScenarioPlayer claimedThenLeft(String name) {
        ScenarioPlayer player = running.join(name);
        running.run(player, "kit starter");
        assertThat(RunningPlugin.said(player)).anyMatch(line -> line.contains(HANDED_OUT));
        running.run(player, "kit starter");
        assertThat(RunningPlugin.said(player)).noneMatch(line -> line.contains(HANDED_OUT));
        running.quit(player);
        return player;
    }

    /** The same account joins again with the player data it left with and tries the kit. */
    private boolean claimsAgain(ScenarioPlayer away) {
        ScenarioPlayer back = running.join(
                away.getName(),
                joining -> away.getPersistentDataContainer().copyTo(joining.getPersistentDataContainer(), true));
        RunningPlugin.said(back);
        running.run(back, "kit starter");
        return RunningPlugin.said(back).stream().anyMatch(line -> line.contains(HANDED_OUT));
    }
}
