package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Location;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Commands whose target is kept by account reach a player who logged off: staff go to an offline player's home, and a
 * player blocks or unblocks the requests of someone who is not online.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OfflineTargetScenarioTest {

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
    void staffGoToAHomeOfAPlayerWhoLoggedOff() {
        ScenarioPlayer staff = running.join("Stella");
        staff.setOp(true);
        ScenarioPlayer owner = running.join("Quinn");
        Location home = new Location(running.world, 10.5, 5, 10.5);
        staff.teleport(home);
        running.run(staff, "home admin Quinn set 1");
        running.quit(owner);

        staff.teleport(new Location(running.world, 60.5, 5, 60.5));
        running.run(staff, "home admin Quinn tp 1");
        // Long enough for the shipped warmup, should the staff teleport have one.
        for (int second = 0; second < 4; second++) {
            running.settle();
        }

        assertThat(staff.getLocation().getBlockX()).isEqualTo(home.getBlockX());
        assertThat(staff.getLocation().getBlockZ()).isEqualTo(home.getBlockZ());
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aPlayerWhoLoggedOffIsBlockedAndUnblocked() {
        ScenarioPlayer alice = running.join("Alice");
        ScenarioPlayer bob = running.join("Bob");
        running.quit(bob);

        running.run(alice, "tpblock Bob");
        bob = running.join("Bob");
        RunningPlugin.said(alice);
        running.run(bob, "tpa Alice");
        assertThat(RunningPlugin.said(alice))
                .as("a blocked player's request never reaches Alice")
                .isEmpty();

        running.quit(bob);
        running.run(alice, "tpunblock Bob");
        bob = running.join("Bob");
        RunningPlugin.said(alice);
        running.run(bob, "tpa Alice");
        assertThat(RunningPlugin.said(alice))
                .as("the request reaches Alice once Bob is unblocked")
                .isNotEmpty();
        assertThat(running.problems()).isEmpty();
    }
}
