package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
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
            }
            """;

    private static final String CLICK = "<click:run_command:'/op Mallory'>free diamonds</click>";

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
