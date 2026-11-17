package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;

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

    /** {@code line} through PlaceholderAPI with only the player expansion installed, which names the reader Staff. */
    static String fill(String line) {
        return new CharsReplacer(Replacer.Closure.PERCENT)
                .apply(line, null, identifier -> identifier.equals("player") ? new PlayerExpansion() : null);
    }

    static String visible(String text) {
        return text.replace(String.valueOf(PlaceholderShield.BREAK), "");
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
