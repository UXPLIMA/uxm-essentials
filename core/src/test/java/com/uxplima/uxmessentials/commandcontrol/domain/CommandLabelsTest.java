package com.uxplima.uxmessentials.commandcontrol.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The auto-lowercase base-label normalisation: only the leading command label is lowered while a leading slash and
 * every argument (target names, world names, message bodies) are preserved exactly, so {@code /GAMEMODE Creative}
 * becomes {@code /gamemode Creative} and is matched and executed as {@code gamemode}.
 */
class CommandLabelsTest {

    @Test
    void anUppercaseBaseLabelIsLoweredWithASlashPreserved() {
        assertThat(CommandLabels.lowerBaseLabel("/GAMEMODE")).isEqualTo("/gamemode");
        assertThat(CommandLabels.lowerBaseLabel("/GameMode")).isEqualTo("/gamemode");
    }

    @Test
    void onlyTheBaseLabelIsLoweredAndArgumentsAreUntouched() {
        // The label is lowered; the argument's casing (a world name, a target player) is kept exactly.
        assertThat(CommandLabels.lowerBaseLabel("/GAMEMODE Creative Steve")).isEqualTo("/gamemode Creative Steve");
        assertThat(CommandLabels.lowerBaseLabel("/TP Steve World_Nether")).isEqualTo("/tp Steve World_Nether");
    }

    @Test
    void aMessageWithNoLeadingSlashIsStillNormalised() {
        assertThat(CommandLabels.lowerBaseLabel("GAMEMODE creative")).isEqualTo("gamemode creative");
    }

    @Test
    void anAlreadyLowercaseMessageIsUnchanged() {
        assertThat(CommandLabels.lowerBaseLabel("/gamemode Creative")).isEqualTo("/gamemode Creative");
    }

    @Test
    void aBlankMessageIsReturnedUnchanged() {
        assertThat(CommandLabels.lowerBaseLabel("")).isEmpty();
        assertThat(CommandLabels.lowerBaseLabel("   ")).isEqualTo("   ");
    }
}
