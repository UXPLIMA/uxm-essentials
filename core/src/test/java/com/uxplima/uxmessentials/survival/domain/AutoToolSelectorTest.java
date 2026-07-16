package com.uxplima.uxmessentials.survival.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.OptionalInt;

import com.uxplima.uxmessentials.survival.domain.AutoToolSelector.HeldTool;
import org.junit.jupiter.api.Test;

/**
 * Pins the pure autotool selection: the strongest tool of the block's family wins, blocks classify to the right family,
 * and a block needing no tool (or a hotbar with no matching tool) yields no switch.
 */
class AutoToolSelectorTest {

    private final AutoToolSelector selector = new AutoToolSelector();

    @Test
    void picksTheStrongestPickaxeForStone() {
        List<HeldTool> hotbar = List.of(
                new HeldTool(0, "WOODEN_PICKAXE"), new HeldTool(3, "DIAMOND_PICKAXE"), new HeldTool(5, "IRON_SHOVEL"));

        assertThat(selector.bestSlot("STONE", hotbar)).isEqualTo(OptionalInt.of(3));
    }

    @Test
    void picksTheAxeForALog() {
        List<HeldTool> hotbar = List.of(new HeldTool(1, "STONE_PICKAXE"), new HeldTool(4, "IRON_AXE"));

        assertThat(selector.bestSlot("OAK_LOG", hotbar)).isEqualTo(OptionalInt.of(4));
    }

    @Test
    void tiesResolveToTheLowestSlot() {
        List<HeldTool> hotbar = List.of(new HeldTool(7, "DIAMOND_PICKAXE"), new HeldTool(2, "DIAMOND_PICKAXE"));

        assertThat(selector.bestSlot("IRON_ORE", hotbar)).isEqualTo(OptionalInt.of(2));
    }

    @Test
    void aHotbarWithNoMatchingToolYieldsNoSwitch() {
        List<HeldTool> hotbar = List.of(new HeldTool(0, "DIAMOND_AXE"), new HeldTool(1, "STONE"));

        assertThat(selector.bestSlot("STONE", hotbar)).isEmpty();
    }

    @Test
    void aBlockThatNeedsNoToolYieldsNoSwitch() {
        List<HeldTool> hotbar = List.of(new HeldTool(0, "DIAMOND_PICKAXE"));

        assertThat(selector.bestSlot("OAK_SAPLING", hotbar)).isEmpty();
    }

    @Test
    void classifiesBlocksToTheirToolFamily() {
        assertThat(selector.requiredToolFor("DEEPSLATE_DIAMOND_ORE")).isEqualTo(ToolType.PICKAXE);
        assertThat(selector.requiredToolFor("BIRCH_LOG")).isEqualTo(ToolType.AXE);
        assertThat(selector.requiredToolFor("GRAVEL")).isEqualTo(ToolType.SHOVEL);
        assertThat(selector.requiredToolFor("OAK_LEAVES")).isEqualTo(ToolType.HOE);
        assertThat(selector.requiredToolFor("COBWEB")).isEqualTo(ToolType.SWORD);
        assertThat(selector.requiredToolFor("WHITE_CONCRETE_POWDER")).isEqualTo(ToolType.SHOVEL);
        assertThat(selector.requiredToolFor("WHITE_CONCRETE")).isEqualTo(ToolType.PICKAXE);
        assertThat(selector.requiredToolFor("TORCH")).isEqualTo(ToolType.NONE);
    }
}
