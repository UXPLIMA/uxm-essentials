package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Editors driven with nothing but left clicks, the one click a Bedrock player in a chest menu can always send: every
 * change an editor offers on a shift or right click is reachable this way too.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LeftClickEditorScenarioTest {

    /** Prompts read the next chat line, so a scenario can answer one. */
    private static final String TEXT_INPUT = """
            default-mode = chat
            cancel-keywords = ["cancel"]
            """;

    private static final int WELCOME_BUTTON = 12;
    private static final int WELCOME_ADD = 18;
    private static final int FIRST_MESSAGE = 0;
    private static final int PANEL_TYPE = 13;
    private static final int PANEL_BACK = 22;

    /** Where the shipped example menu puts its compass tile. */
    private static final int EXAMPLE_COMPASS_SLOT = 11;

    private RunningPlugin running;

    @BeforeAll
    void start() {
        running = RunningPlugin.start(Map.of("text-input.conf", TEXT_INPUT));
    }

    @AfterAll
    void stop() {
        running.close();
    }

    @Test
    void aWelcomeMessageChangesHowItIsShownWithLeftClicksOnly() {
        ScenarioPlayer operator = running.join("Wren");
        operator.setOp(true);
        running.run(operator, "warp create harbor");
        running.run(operator, "warp editor harbor");
        running.click(operator, WELCOME_BUTTON);
        running.click(operator, WELCOME_ADD);
        running.chat(operator, "welcome to the harbor");
        assertThat(material(operator, FIRST_MESSAGE))
                .as("the new message, shown in chat")
                .isEqualTo(Material.PAPER);

        running.click(operator, FIRST_MESSAGE);
        assertThat(material(operator, PANEL_TYPE)).as("the message's panel").isEqualTo(Material.PAPER);
        running.click(operator, PANEL_TYPE);

        assertThat(material(operator, PANEL_TYPE))
                .as("the panel again, on the action bar")
                .isEqualTo(Material.REPEATER);
        running.click(operator, PANEL_BACK);
        assertThat(material(operator, FIRST_MESSAGE))
                .as("the list, read back from the warp")
                .isEqualTo(Material.REPEATER);
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aMenuItemIsTakenOffItsSlotWithLeftClicksOnly() {
        ScenarioPlayer operator = running.join("Mika");
        operator.setOp(true);
        running.run(operator, "menu editor");
        running.click(operator, running.slotOf(operator, Material.CHEST));
        running.click(operator, running.slotOf(operator, Material.CRAFTING_TABLE));
        assertThat(material(operator, EXAMPLE_COMPASS_SLOT)).as("the grid").isEqualTo(Material.COMPASS);

        running.click(operator, EXAMPLE_COMPASS_SLOT);
        running.click(operator, running.slotOf(operator, Material.LAVA_BUCKET));
        running.click(operator, running.slotOf(operator, Material.LIME_WOOL));

        assertThat(material(operator, EXAMPLE_COMPASS_SLOT))
                .as("the grid again, the cell empty")
                .isEqualTo(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        assertThat(running.problems()).isEmpty();
    }

    private static @Nullable Material material(ScenarioPlayer player, int slot) {
        Inventory top = player.getOpenInventory().getTopInventory();
        @Nullable ItemStack item = top.getItem(slot);
        return item == null ? null : item.getType();
    }
}
