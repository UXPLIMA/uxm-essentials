package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * What a player types into a menu's own open command reads as the words they typed in the line its button broadcasts,
 * and runs nothing for whoever reads it.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MenuArgumentScenarioTest {

    private static final String MENU = """
            command {
              name = "herald"
              arguments = [
                { name = "words", type = string }
              ]
            }
            title = "Herald"
            rows = 1
            items {
              shout {
                slot = 0
                material = PAPER
                name = "Shout"
                click {
                  left = ["broadcast:<gold>%argument_words%</gold>"]
                }
              }
              json {
                slot = 1
                material = PAPER
                name = "Shout in JSON"
                click {
                  left = ["broadcast-json:{\\\"text\\\":\\\"%argument_words%\\\"}"]
                }
              }
              legacy {
                slot = 2
                material = PAPER
                name = "Shout in green"
                click {
                  left = ["broadcast-legacy:&a%argument_words%"]
                }
              }
            }
            """;

    private static final String CLICK = "<click:run_command:'/op Mallory'>free diamonds</click>";

    private static final String QUOTE = "\"";

    private static final String BACKSLASH = "\\";

    /** What a legacy code a player typed is written as: a full-width ampersand, which reads the same and is no code. */
    private static final String FULLWIDTH_AMPERSAND = "\uff06";

    private RunningPlugin running;

    @BeforeAll
    void start() {
        running = RunningPlugin.start(Map.of("menus/herald.conf", MENU));
    }

    @AfterAll
    void stop() {
        running.close();
    }

    @Test
    void aTypedTagIsBroadcastAsWords() {
        ScenarioPlayer mallory = running.join("Mallory");
        ScenarioPlayer reader = running.join("Reader");

        running.run(mallory, "herald \"" + CLICK + "\"");
        running.settle();
        assertThat(mallory.getOpenInventory().getTopInventory().getItem(0)).isNotNull();
        heardBy(reader);
        running.click(mallory, 0);
        running.settle();

        List<Component> lines = heardBy(reader);
        assertThat(lines).anySatisfy(line -> assertThat(plain(line)).isEqualTo(CLICK));
        assertNoClicks(lines);
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aTypedQuoteAndClickStayTheWordsTypedInAJsonBroadcast() {
        ScenarioPlayer mallory = running.join("Malone");
        ScenarioPlayer reader = running.join("Rhea");
        String typed = "it's " + QUOTE + "free" + QUOTE + "," + QUOTE + "clickEvent" + QUOTE + ":{} " + CLICK;

        running.run(mallory, "herald " + QUOTE + typed.replace(QUOTE, BACKSLASH + QUOTE) + QUOTE);
        running.settle();
        heardBy(reader);
        running.click(mallory, 1);
        running.settle();

        List<Component> lines = heardBy(reader);
        assertThat(lines).anySatisfy(line -> assertThat(plain(line)).isEqualTo(typed));
        assertNoClicks(lines);
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aTypedColourCodeStaysTheWordsTypedInALegacyBroadcast() {
        ScenarioPlayer mallory = running.join("Mallet");
        ScenarioPlayer reader = running.join("Reese");

        running.run(mallory, "herald &cred");
        running.settle();
        heardBy(reader);
        running.click(mallory, 2);
        running.settle();

        List<Component> lines = heardBy(reader);
        assertThat(lines).anySatisfy(line -> {
            assertThat(plain(line)).isEqualTo(FULLWIDTH_AMPERSAND + "cred");
            assertThat(colours(line)).containsOnly(NamedTextColor.GREEN);
        });
        assertThat(running.problems()).isEmpty();
    }

    /** Every colour a part of {@code line} that holds words is drawn in, a part with none taking its parent's. */
    private static List<@Nullable TextColor> colours(Component line) {
        List<@Nullable TextColor> found = new ArrayList<>();
        collectColours(line, null, found);
        return found;
    }

    private static void collectColours(Component part, @Nullable TextColor around, List<@Nullable TextColor> found) {
        @Nullable TextColor colour = part.color() != null ? part.color() : around;
        if (part instanceof TextComponent text && !text.content().isEmpty()) {
            found.add(colour);
        }
        part.children().forEach(child -> collectColours(child, colour, found));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
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
}
