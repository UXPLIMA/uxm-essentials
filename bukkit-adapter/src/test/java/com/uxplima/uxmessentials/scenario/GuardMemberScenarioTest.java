package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** Staff giving a region role to a player who is not online, and reading the region's members back by name. */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GuardMemberScenarioTest {

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
    void aPlayerWhoLeftIsMadeAMemberAndListedByName() {
        ScenarioPlayer staff = running.join("Gale");
        staff.setOp(true);
        ScenarioPlayer member = running.join("Olwen");
        String account = member.getUniqueId().toString().substring(0, 8);
        running.quit(member);
        running.run(staff, "guard define plaza global");
        RunningPlugin.said(staff);

        running.run(staff, "guard member plaza Olwen member");
        List<String> granted = RunningPlugin.said(staff);
        running.run(staff, "guard info plaza");
        List<String> info = RunningPlugin.said(staff);

        assertThat(granted).anyMatch(line -> line.contains("Olwen"));
        assertThat(info).anyMatch(line -> line.contains("Olwen")).noneMatch(line -> line.contains(account));
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aNameNobodyHoldsIsRefused() {
        ScenarioPlayer staff = running.join("Gwen");
        staff.setOp(true);
        running.run(staff, "guard define square global");
        RunningPlugin.said(staff);

        running.run(staff, "guard member square Nobody member");

        assertThat(RunningPlugin.said(staff)).singleElement().asString().contains("Nobody");
        assertThat(running.problems()).isEmpty();
    }
}
