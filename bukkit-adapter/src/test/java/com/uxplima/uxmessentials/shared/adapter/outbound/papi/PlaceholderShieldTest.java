package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.OfflinePlayer;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.replacer.CharsReplacer;
import me.clip.placeholderapi.replacer.Replacer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A shielded value goes through PlaceholderAPI's own parser as typed, while the placeholders around it are filled, and
 * it reads the same once the invisible break is set aside.
 */
class PlaceholderShieldTest {

    @Test
    void aPlaceholderInAShieldedValueIsLeftAsTyped() {
        String line = "hi " + PlaceholderShield.shield("%player_name% and %player_ip%");

        assertThat(visible(fill(line))).isEqualTo("hi %player_name% and %player_ip%");
    }

    @Test
    void theOperatorsPlaceholderNextToAShieldedValueIsStillFilled() {
        String line = "%player_name% said " + PlaceholderShield.shield("50%") + "player_name% today";

        assertThat(visible(fill(line))).isEqualTo("Staff said 50%player_name% today");
    }

    @Test
    void textWithoutAPercentIsUnchangedAndShieldingTwiceIsShieldingOnce() {
        String once = PlaceholderShield.shield("a %b% c");

        assertThat(PlaceholderShield.shield("plain text")).isEqualTo("plain text");
        assertThat(PlaceholderShield.shield(once)).isEqualTo(once);
        assertThat(visible(once)).isEqualTo("a %b% c");
    }

    @Test
    void aValuesPercentCannotCloseAPlaceholderTheOperatorOpened() {
        // Two typed values around the operator's placeholder: the first one's percent would end a lookup at the
        // operator's opening percent, and the operator's closing percent would then open %player_ip% up to the
        // second one's.
        String line =
                PlaceholderShield.shield("%x_y") + " <gray>%luckperms_prefix%" + PlaceholderShield.shield("player_ip%");

        String read = PlaceholderShield.around(PlaceholderShieldTest::fill).apply(line);

        assertThat(fill(line))
                .as("the mark alone lets the reader's address through")
                .contains("203.0.113.7");

        assertThat(visible(read)).isEqualTo("%x_y <gray>[VIP]player_ip%");
    }

    @Test
    void aValueThatStartsWithTheBreakCannotTakeTheOperatorsPercent() {
        String line = "%player_name%" + PlaceholderShield.shield("\u200C, hi");

        assertThat(visible(PlaceholderShield.around(PlaceholderShieldTest::fill).apply(line)))
                .isEqualTo("Staff, hi");
    }

    @Test
    void whatTheShieldHidesComesBackAsItWas() {
        String line = "a " + PlaceholderShield.shield("50% off %b%") + " %c_d%";

        List<String> read = new ArrayList<>();

        assertThat(PlaceholderShield.around(text -> {
                            read.add(text);
                            return text;
                        })
                        .apply(line))
                .isEqualTo(line);
        assertThat(read)
                .singleElement()
                .satisfies(seen -> assertThat(seen.chars().filter(c -> c == '%'))
                        .as("only the operator's two percent signs")
                        .hasSize(2));
        assertThat(PlaceholderShield.unshield(line)).isEqualTo("a 50% off %b% %c_d%");
    }

    @Test
    void theNoncharacterCannotHideABreakAtTheStartOfAValue() {
        String withPercent = "%player_name%" + PlaceholderShield.shield("\uFDD0\u200C%x");
        String twice = "%player_name%" + PlaceholderShield.shield("\uFDD0\uFDD0\u200Cx");

        assertThat(visible(PlaceholderShield.around(PlaceholderShieldTest::fill).apply(withPercent)))
                .isEqualTo("Staff%x");
        assertThat(visible(PlaceholderShield.around(PlaceholderShieldTest::fill).apply(twice)))
                .isEqualTo("Staffx");
    }

    @Test
    void aNoncharacterThatWasNotAValuesPercentIsLeftAlone() {
        // One from a datapack title the shield never saw, and one an expansion answers with.
        String line = "title\uFDD0 " + PlaceholderShield.shield("50%");

        String read = PlaceholderShield.around(text -> text + "\uFDD0").apply(line);

        assertThat(read).isEqualTo(line + "\uFDD0");
        assertThat(PlaceholderShield.unshield(read)).isEqualTo("title\uFDD0 50%\uFDD0");
    }

    /**
     * {@code line} through PlaceholderAPI with the player expansion, which names the reader Staff, and LuckPerms', which
     * gives everyone the prefix [VIP].
     */
    static String fill(String line) {
        return new CharsReplacer(Replacer.Closure.PERCENT).apply(line, null, identifier -> switch (identifier) {
            case "player" -> new PlayerExpansion();
            case "luckperms" -> new PrefixExpansion();
            default -> null;
        });
    }

    static String visible(String text) {
        return text.replace(String.valueOf(PlaceholderShield.BREAK), "");
    }

    /** LuckPerms' expansion, as far as these lines ask it. */
    static final class PrefixExpansion extends PlaceholderExpansion {

        @Override
        public String getIdentifier() {
            return "luckperms";
        }

        @Override
        public String getAuthor() {
            return "test";
        }

        @Override
        public String getVersion() {
            return "1";
        }

        @Override
        public @Nullable String onRequest(@Nullable OfflinePlayer player, String params) {
            return params.equals("prefix") ? "[VIP]" : null;
        }
    }

    /** PlaceholderAPI's player expansion, as far as these lines ask it. */
    static final class PlayerExpansion extends PlaceholderExpansion {

        @Override
        public String getIdentifier() {
            return "player";
        }

        @Override
        public String getAuthor() {
            return "test";
        }

        @Override
        public String getVersion() {
            return "1";
        }

        @Override
        public @Nullable String onRequest(@Nullable OfflinePlayer player, String params) {
            return switch (params) {
                case "name" -> player == null ? "Staff" : player.getName();
                case "ip" -> "203.0.113.7";
                default -> null;
            };
        }
    }
}
