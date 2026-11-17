package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * A menu's whisper action is a private message from the player who clicks, sent as {@code /msg} sends it, so the
 * receiver's ignore list applies; naming the clicker shows them the text.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MenuWhisperScenarioTest {

    private static final String MENU = """
            title = "Whispers"
            rows = 1
            items {
              toBree {
                slot = 0
                material = PAPER
                click {
                  left = ["whisper:Bree hello from the menu"]
                }
              }
              toSelf {
                slot = 1
                material = PAPER
                click {
                  left = ["message-to:Cleo a note for you"]
                }
              }
              toNobody {
                slot = 2
                material = PAPER
                click {
                  left = ["whisper:Nobody are you there"]
                }
              }
            }
            """;

    private RunningPlugin running;

    @BeforeAll
    void start() {
        running = RunningPlugin.start(Map.of("menus/whispers.conf", MENU));
    }

    @AfterAll
    void stop() {
        running.close();
    }

    @Test
    void aWhisperReachesThePlayerAsAPrivateMessageUnlessTheyIgnoreTheClicker() {
        ScenarioPlayer clicker = running.join("Arlo");
        ScenarioPlayer bree = running.join("Bree");
        clicker.setOp(true);
        running.run(clicker, "menu open whispers");
        RunningPlugin.said(bree);

        running.click(clicker, 0);
        assertThat(RunningPlugin.said(bree)).anyMatch(line -> line.contains("hello from the menu"));

        running.run(bree, "ignore Arlo");
        RunningPlugin.said(bree);
        running.run(clicker, "menu open whispers");
        running.click(clicker, 0);
        assertThat(RunningPlugin.said(bree)).noneMatch(line -> line.contains("hello from the menu"));
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aWhisperToTheClickerShowsThemTheTextAndOneToNobodySaysSo() {
        ScenarioPlayer clicker = running.join("Cleo");
        clicker.setOp(true);
        running.run(clicker, "menu open whispers");
        RunningPlugin.said(clicker);

        running.click(clicker, 1);
        assertThat(RunningPlugin.said(clicker)).containsExactly("a note for you");

        running.run(clicker, "menu open whispers");
        running.click(clicker, 2);
        assertThat(RunningPlugin.said(clicker)).anyMatch(line -> line.contains("Nobody"));
        assertThat(running.problems()).isEmpty();
    }
}
