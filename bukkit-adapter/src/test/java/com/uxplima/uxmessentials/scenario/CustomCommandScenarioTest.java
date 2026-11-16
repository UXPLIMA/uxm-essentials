package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.bukkit.Location;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockbukkit.mockbukkit.simulate.entity.PlayerSimulation;

/** A custom command an operator wrote with a warmup, run by players who stand still and players who walk off. */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomCommandScenarioTest {

    private static final String RAN = "reward handed out";
    private static final String REWARD = "command {\n"
            + "  name = \"reward\"\n"
            + "  description = \"A reward after a short wait\"\n"
            + "  warmup = \"3s\"\n"
            + "  cost = 0\n"
            + "}\n"
            + "actions = [\"message:" + RAN + "\"]\n";

    private RunningPlugin running;

    @BeforeAll
    void start() {
        running = RunningPlugin.start(Map.of("commands/custom/reward.conf", REWARD));
    }

    @AfterAll
    void stop() {
        running.close();
    }

    @Test
    void aPlayerWhoStandsStillThroughTheWarmupGetsTheReward() {
        ScenarioPlayer player = running.join("Rita");

        running.run(player, "reward");
        waitOutTheWarmup();

        assertThat(RunningPlugin.said(player))
                .anySatisfy(line -> assertThat(line).contains(RAN));
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void walkingOffStopsTheWarmupEvenAfterAnotherPlayersWasStoppedTheSameWay() {
        ScenarioPlayer alex = running.join("Alex");
        ScenarioPlayer sam = running.join("Sam");

        running.run(alex, "reward");
        walkOff(alex);
        running.run(sam, "reward");
        walkOff(sam);
        waitOutTheWarmup();

        assertThat(RunningPlugin.said(alex))
                .noneSatisfy(line -> assertThat(line).contains(RAN));
        assertThat(RunningPlugin.said(sam))
                .as("the move listener still watched the second player after the first one's warmup ended")
                .noneSatisfy(line -> assertThat(line).contains(RAN));
        assertThat(running.problems()).isEmpty();
    }

    /** A step of four blocks, raised as the server raises a player's move. */
    private void walkOff(ScenarioPlayer player) {
        Location to = player.getLocation().clone().add(4, 0, 0);
        new PlayerSimulation(player).simulatePlayerMove(to);
        running.settle();
    }

    private void waitOutTheWarmup() {
        for (int second = 0; second < 4; second++) {
            running.settle();
        }
    }
}
