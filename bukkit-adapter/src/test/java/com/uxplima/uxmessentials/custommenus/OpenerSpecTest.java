package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.bukkit.Material;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerSpec;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerSpec.GiveOnJoin;
import org.junit.jupiter.api.Test;

/**
 * Pure coverage of the {@link OpenerSpec} value type and its {@link GiveOnJoin} parse: the give-on-join tokens map
 * to the right modes (with synonyms), an unrecognised token is an empty result the loader defaults, and the record
 * validates a non-blank menu id and a non-air material at construction.
 */
class OpenerSpecTest {

    @Test
    void giveOnJoinParsesTheThreeModesWithSynonyms() {
        assertThat(GiveOnJoin.parse("never")).contains(GiveOnJoin.NEVER);
        assertThat(GiveOnJoin.parse("off")).contains(GiveOnJoin.NEVER);
        assertThat(GiveOnJoin.parse("first")).contains(GiveOnJoin.FIRST);
        assertThat(GiveOnJoin.parse("once")).contains(GiveOnJoin.FIRST);
        assertThat(GiveOnJoin.parse("ALWAYS")).contains(GiveOnJoin.ALWAYS);
        assertThat(GiveOnJoin.parse("every")).contains(GiveOnJoin.ALWAYS);
    }

    @Test
    void giveOnJoinIsEmptyForAnUnknownOrNullToken() {
        assertThat(GiveOnJoin.parse("sometimes")).isEmpty();
        assertThat(GiveOnJoin.parse(null)).isEmpty();
    }

    @Test
    void aSpecCarriesItsMenuItemSlotAndMode() {
        OpenerSpec spec = new OpenerSpec(
                "hub",
                new OpenerSpec.Item(Material.COMPASS, "<gold>Menu", List.of("<gray>click")),
                4,
                GiveOnJoin.FIRST);

        assertThat(spec.menu()).isEqualTo("hub");
        assertThat(spec.item().material()).isEqualTo(Material.COMPASS);
        assertThat(spec.item().name()).isEqualTo("<gold>Menu");
        assertThat(spec.item().lore()).containsExactly("<gray>click");
        assertThat(spec.slot()).isEqualTo(4);
        assertThat(spec.giveOnJoin()).isEqualTo(GiveOnJoin.FIRST);
    }

    @Test
    void aBlankMenuIdIsRejected() {
        assertThatThrownBy(() ->
                        new OpenerSpec("  ", new OpenerSpec.Item(Material.COMPASS, "", List.of()), 0, GiveOnJoin.NEVER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anAirMaterialIsRejected() {
        assertThatThrownBy(() -> new OpenerSpec.Item(Material.AIR, "", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
