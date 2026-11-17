package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Custom commands an operator wrote to run console lines with what the player typed in them, run by players who type
 * a name and by players who type a selector, spaces or JSON instead.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomCommandArgumentsScenarioTest {

    // The console line the operator wants run: an announcement naming the player, through another custom command.
    private static final String GIFT = """
            command {
              name = "gift"
              description = "Announce a gift for a player"
              cost = 0
            }
            arguments = [
              { name = "target", type = player }
            ]
            actions = ["console:herald gift-for-%arg_target%"]
            """;

    // The same line with the menu engine's own token, which the engine fills after our expansion, and a space after
    // the colon the way people write it.
    private static final String PRESENT = """
            command {
              name = "present"
              description = "Announce a present for a player"
              cost = 0
            }
            arguments = [
              { name = "target", type = player }
            ]
            actions = ["console: herald present-for-%argument_target%"]
            """;

    // A JSON line with the typed value inside a string.
    private static final String TELL = """
            command {
              name = "jsonsay"
              description = "Tell everyone a word"
              cost = 0
            }
            arguments = [
              { name = "word", type = string }
            ]
            actions = ["console:tellraw @a {\\"text\\":\\"%arg_word%\\"}"]
            """;

    // What the console line runs: a broadcast of the one word it is given.
    private static final String HERALD = """
            command {
              name = "herald"
              description = "Announce a word"
              cost = 0
            }
            arguments = [
              { name = "word", type = string }
            ]
            actions = ["broadcast:announced %arg_word%"]
            """;

    // A console line that takes a whole sentence, which the operator let in as text.
    private static final String NOTICE = """
            command {
              name = "notice"
              description = "Post a notice"
              cost = 0
            }
            arguments = [
              { name = "note", type = rest, in-commands = "text" }
            ]
            actions = ["console:crier %arg_note%"]
            """;

    // The same console line for an argument the operator did not let in as text.
    private static final String MEMO = """
            command {
              name = "memo"
              description = "Post a memo"
              cost = 0
            }
            arguments = [
              { name = "note", type = rest }
            ]
            actions = ["console:crier %arg_note%"]
            """;

    // What the sentence lines run: a broadcast of everything they are given.
    private static final String CRIER = """
            command {
              name = "crier"
              description = "Cry out a sentence"
              cost = 0
            }
            arguments = [
              { name = "words", type = rest }
            ]
            actions = ["broadcast:cried %arg_words%"]
            """;

    private static final String REFUSED = "can't take that here";

    private RunningPlugin running;

    @BeforeAll
    void start() {
        running = RunningPlugin.start(Map.of(
                "commands/custom/gift.conf",
                GIFT,
                "commands/custom/present.conf",
                PRESENT,
                "commands/custom/jsonsay.conf",
                TELL,
                "commands/custom/herald.conf",
                HERALD,
                "commands/custom/notice.conf",
                NOTICE,
                "commands/custom/memo.conf",
                MEMO,
                "commands/custom/crier.conf",
                CRIER,
                // The menu engine runs a console line only where the operator lets menus do so.
                "modules/custommenus/config.conf",
                "enabled = true\nallow-console = true\n"));
    }

    @AfterAll
    void stop() {
        running.close();
    }

    @Test
    void aPlayerNameRunsTheConsoleLine() {
        ScenarioPlayer giver = running.join("Giver");
        ScenarioPlayer reader = running.join("Reader");

        running.run(giver, "gift Steve");
        running.run(giver, "present Steve");
        running.settle();
        running.console("herald direct");

        assertThat(RunningPlugin.said(reader))
                .contains("announced direct", "announced gift-for-Steve", "announced present-for-Steve");
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aSelectorIsRefusedAndTheConsoleRunsNothing() {
        ScenarioPlayer mallory = running.join("Mallory");
        ScenarioPlayer reader = running.join("Rita");

        running.run(mallory, "gift @a");
        running.run(mallory, "present @a");

        assertThat(RunningPlugin.saidPlain(mallory))
                .filteredOn(line -> line.contains(REFUSED))
                .hasSize(2);
        assertThat(RunningPlugin.said(reader)).noneMatch(line -> line.contains("announced"));
    }

    @Test
    void aValueWithSpacesIsRefusedAndTheConsoleRunsNothing() {
        ScenarioPlayer mallory = running.join("Mallet");
        ScenarioPlayer reader = running.join("Rory");

        running.run(mallory, "gift \"Steve diamond 64\"");

        assertThat(RunningPlugin.saidPlain(mallory)).anyMatch(line -> line.contains(REFUSED));
        assertThat(RunningPlugin.said(reader)).noneMatch(line -> line.contains("announced"));
    }

    @Test
    void anArgumentLetInAsTextTakesASentenceIntoTheConsoleLine() {
        ScenarioPlayer poster = running.join("Poster");
        ScenarioPlayer reader = running.join("Reese");

        running.run(poster, "notice the farm at spawn is broken");
        running.run(poster, "memo the farm at spawn is broken");

        assertThat(RunningPlugin.said(reader)).containsOnlyOnce("cried the farm at spawn is broken");
        assertThat(RunningPlugin.saidPlain(poster)).anyMatch(line -> line.contains(REFUSED));
    }

    @Test
    void jsonTypedIntoAConsoleLineIsRefused() {
        ScenarioPlayer mallory = running.join("Malone");

        running.run(mallory, "jsonsay \"x\\\",\\\"clickEvent\\\":{\\\"action\\\":\\\"run_command\\\"}\"");

        assertThat(RunningPlugin.saidPlain(mallory)).anyMatch(line -> line.contains(REFUSED));
    }
}
