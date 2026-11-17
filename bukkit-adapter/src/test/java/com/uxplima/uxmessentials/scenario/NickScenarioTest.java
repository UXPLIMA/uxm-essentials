package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * A player's {@code /nick} is worn again after a relog, a nick copying another player's name is refused, and one that
 * copied a name before that name joined is taken back; an operator sets the nick of a player who is offline; a nick an
 * earlier version left in the player's data is taken over, unless it is another player's name.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NickScenarioTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

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
    void aNickIsWornAgainAfterARelog() {
        ScenarioPlayer player = running.join("Nadia");
        player.setOp(true);

        running.run(player, "nick Captain");
        running.settle();
        assertThat(PLAIN.serialize(player.displayName())).isEqualTo("Captain");
        running.quit(player);

        ScenarioPlayer back = running.join("Nadia");
        running.settle();

        assertThat(PLAIN.serialize(back.displayName())).isEqualTo("Captain");
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void anotherPlayersNameIsRefused() {
        running.quit(running.join("Victor"));
        ScenarioPlayer player = running.join("Nell");
        player.setOp(true);
        RunningPlugin.said(player);

        running.run(player, "nick victor");
        running.settle();

        assertThat(PLAIN.serialize(player.displayName())).isEqualTo("Nell");
        assertThat(RunningPlugin.said(player)).anyMatch(line -> line.contains("victor"));
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void anOperatorSetsTheNickOfAPlayerWhoIsOffline() {
        running.quit(running.join("Otto"));
        ScenarioPlayer staff = running.join("Olga");
        staff.setOp(true);

        running.run(staff, "nick Otto Admiral");
        running.settle();
        ScenarioPlayer back = running.join("Otto");
        running.settle();

        assertThat(PLAIN.serialize(back.displayName())).isEqualTo("Admiral");
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aNickLeftInThePlayerDataByAnEarlierVersionIsTakenOver() {
        NamespacedKey legacy = new NamespacedKey("uxmessentials", "display-nick");
        ScenarioPlayer player = running.join(
                "Leah",
                joining -> joining.getPersistentDataContainer().set(legacy, PersistentDataType.STRING, "Seafarer"));
        running.settle();

        assertThat(PLAIN.serialize(player.displayName())).isEqualTo("Seafarer");
        assertThat(player.getPersistentDataContainer().has(legacy)).isFalse();
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aNameThatJoinsTakesBackTheNickAnotherPlayerCopiedFromIt() {
        ScenarioPlayer player = running.join("Ivy");
        player.setOp(true);
        running.run(player, "nick Oscar");
        running.settle();
        assertThat(PLAIN.serialize(player.displayName())).isEqualTo("Oscar");

        running.join("Oscar");
        running.settle();
        running.settle();

        assertThat(PLAIN.serialize(player.displayName())).isEqualTo("Ivy");
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aLegacyNickThatIsAnotherPlayersNameIsDropped() {
        running.quit(running.join("Vera"));
        NamespacedKey legacy = new NamespacedKey("uxmessentials", "display-nick");
        ScenarioPlayer player = running.join(
                "Lena", joining -> joining.getPersistentDataContainer().set(legacy, PersistentDataType.STRING, "vera"));
        running.settle();

        assertThat(PLAIN.serialize(player.displayName())).isEqualTo("Lena");
        assertThat(player.getPersistentDataContainer().has(legacy)).isFalse();
        assertThat(running.problems()).isEmpty();
    }
}
