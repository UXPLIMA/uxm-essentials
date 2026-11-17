package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Console and operator steps on a server whose operator turned console actions off for custom commands, and on for
 * menus with the operator action off: each kind of file answers to its own module's switches only.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomCommandPrivilegeScenarioTest {

    private static final String SIGNAL = """
            command {
              name = "signal"
              description = "Signal through the console"
              cost = 0
            }
            actions = ["console:beacon lit", "console-random:beacon lit;beacon lit"]
            """;

    private static final String BEACON = """
            command {
              name = "beacon"
              description = "Show a word"
              cost = 0
            }
            arguments = [
              { name = "word", type = string }
            ]
            actions = ["broadcast:beacon %arg_word%"]
            """;

    private RunningPlugin running;

    @BeforeAll
    void start() {
        running = RunningPlugin.start(Map.of(
                "commands/custom/signal.conf",
                SIGNAL,
                "commands/custom/beacon.conf",
                BEACON,
                "modules/customcommands/config.conf",
                "allow-console-actions = false\n",
                "modules/custommenus/config.conf",
                "enabled = true\nallow-console = true\nallow-op = false\n"));
    }

    @AfterAll
    void stop() {
        running.close();
    }

    @Test
    void aMenuActionFollowsTheMenuSwitches() {
        ScenarioPlayer clicker = running.join("Clicker");
        ScenarioPlayer reader = running.join("Onlooker");

        running.console("menu execute Clicker console-random:beacon menu");
        running.console("menu execute Clicker command-as-op:beacon op");
        running.settle();

        List<String> heard = RunningPlugin.said(reader);
        assertThat(heard).contains("beacon menu");
        assertThat(heard).noneMatch(line -> line.contains("beacon op"));
        assertThat(clicker.isOp()).isFalse();
        assertThat(running.problems())
                .anyMatch(line -> line.contains("menu command-as-op action is disabled (custommenus.allow-op=false)"));
    }

    @Test
    void consoleStepsFollowTheCustomCommandSwitchNotTheMenuOne() {
        ScenarioPlayer signaller = running.join("Signaller");
        ScenarioPlayer reader = running.join("Watcher");

        running.run(signaller, "signal");
        running.settle();

        assertThat(RunningPlugin.said(reader)).noneMatch(line -> line.contains("beacon lit"));
        assertThat(running.problems())
                .anyMatch(line -> line.contains("skipping the 'console' action"))
                .anyMatch(line -> line.contains("skipping the 'console-random' action"));
    }
}
