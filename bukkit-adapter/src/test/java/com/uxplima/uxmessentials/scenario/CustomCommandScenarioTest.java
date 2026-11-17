package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.jspecify.annotations.Nullable;
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

    // An operator's broadcast that puts what the player typed into a styled line.
    private static final String SHOUT = """
            command {
              name = "shout"
              description = "Say something to everyone"
              cost = 0
            }
            arguments = [
              { name = "words", type = rest }
            ]
            actions = ["broadcast:<gold>%arg_words%</gold>"]
            """;

    // The same broadcast written with the menu engine's own token for the argument.
    private static final String YELL = """
            command {
              name = "yell"
              description = "Say something to everyone, loudly"
              cost = 0
            }
            arguments = [
              { name = "words", type = rest }
            ]
            actions = ["broadcast:<red>%argument_words%</red>"]
            """;

    private static final String CLICK = "<click:run_command:'/op Mallory'>free diamonds</click>";

    private RunningPlugin running;

    @BeforeAll
    void start() {
        running = RunningPlugin.start(Map.of(
                "commands/custom/reward.conf",
                REWARD,
                "commands/custom/shout.conf",
                SHOUT,
                "commands/custom/yell.conf",
                YELL));
    }

    @Test
    void whatAPlayerTypesIntoABroadcastIsShownAsWordsAndRunsNothing() {
        ScenarioPlayer mallory = running.join("Mallory");
        ScenarioPlayer reader = running.join("Reader");

        running.run(mallory, "shout " + CLICK);
        running.settle();

        List<Component> lines = heardBy(reader);
        assertThat(lines)
                .anySatisfy(shown -> assertThat(
                                PlainTextComponentSerializer.plainText().serialize(shown))
                        .isEqualTo(CLICK));
        assertNoClicks(lines);
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void anEngineTokenTypedIntoABroadcastIsFilledWithWordsToo() {
        ScenarioPlayer mallory = running.join("Mallet");
        ScenarioPlayer reader = running.join("Reed");

        running.run(mallory, "shout %argument_words% " + CLICK);
        running.settle();

        List<Component> lines = heardBy(reader);
        assertThat(lines).isNotEmpty();
        assertNoClicks(lines);
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aBroadcastWrittenWithTheEngineTokenShowsWhatWasTypedAsWords() {
        ScenarioPlayer mallory = running.join("Malone");
        ScenarioPlayer reader = running.join("Rhea");

        running.run(mallory, "yell " + CLICK);
        running.settle();

        List<Component> lines = heardBy(reader);
        assertThat(lines)
                .anySatisfy(shown -> assertThat(
                                PlainTextComponentSerializer.plainText().serialize(shown))
                        .isEqualTo(CLICK));
        assertNoClicks(lines);
        assertThat(running.problems()).isEmpty();
    }

    private static List<Component> heardBy(ScenarioPlayer reader) {
        List<Component> lines = new ArrayList<>();
        @Nullable Component line;
        while ((line = reader.nextComponentMessage()) != null) {
            lines.add(line);
        }
        return lines;
    }

    private static void assertNoClicks(List<Component> lines) {
        assertThat(lines).allSatisfy(shown -> {
            List<Component> parts = new ArrayList<>();
            shown.iterator(ComponentIteratorType.DEPTH_FIRST).forEachRemaining(parts::add);
            assertThat(parts).allSatisfy(part -> assertThat(part.clickEvent()).isNull());
        });
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
