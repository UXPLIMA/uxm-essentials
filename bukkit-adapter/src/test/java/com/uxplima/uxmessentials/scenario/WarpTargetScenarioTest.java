package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Location;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * The word after {@code /warp <warp>}: a player for staff who may warp others, a password only on a warp that asks for
 * one, so a name that matches nobody never warps the staff member instead.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WarpTargetScenarioTest {

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
    void aNameNobodyHoldsOnAWarpWithoutAPasswordWarpsNobody() {
        ScenarioPlayer staff = running.join("Wendy");
        staff.setOp(true);
        staff.teleport(new Location(running.world, 20.5, 5, 20.5));
        running.run(staff, "warp create fountain");
        Location away = new Location(running.world, 80.5, 5, 80.5);
        staff.teleport(away);
        RunningPlugin.said(staff);

        running.run(staff, "warp fountain Nobody");
        settleWarmup();

        assertThat(staff.getLocation().getBlockX()).isEqualTo(away.getBlockX());
        assertThat(RunningPlugin.said(staff)).singleElement().asString().contains("Nobody");
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aWordOnAWarpWithAPasswordIsStillThePassword() {
        ScenarioPlayer staff = running.join("Wade");
        staff.setOp(true);
        Location warp = new Location(running.world, 30.5, 5, 30.5);
        staff.teleport(warp);
        running.run(staff, "warp create vault");
        running.run(staff, "warp password vault opensesame");
        staff.teleport(new Location(running.world, 90.5, 5, 90.5));

        running.run(staff, "warp vault opensesame");
        settleWarmup();

        assertThat(staff.getLocation().getBlockX()).isEqualTo(warp.getBlockX());
        assertThat(running.problems()).isEmpty();
    }

    /** Long enough for the shipped warmup, should the warp have one. */
    private void settleWarmup() {
        for (int second = 0; second < 6; second++) {
            running.settle();
        }
    }
}
