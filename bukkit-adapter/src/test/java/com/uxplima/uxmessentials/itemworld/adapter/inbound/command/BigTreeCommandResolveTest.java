package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.TreeType;

import org.junit.jupiter.api.Test;

/**
 * Pure-logic coverage of {@code BigTreeCommand#resolve}, the one genuinely new behaviour /bigtree adds over
 * /tree: friendly names map to the large {@link TreeType} variants. The bare {@code tree}/{@code oak} word
 * grows the big oak, {@code jungle} grows the 2x2 mega jungle (contrasting /tree's small sapling), the redwood
 * and dark-oak families pick their mega forms, and an unknown name is empty.
 */
class BigTreeCommandResolveTest {

    @Test
    void bareTreeGrowsBigOak() {
        assertThat(BigTreeCommand.resolve("tree")).contains(TreeType.BIG_TREE);
        assertThat(BigTreeCommand.resolve("oak")).contains(TreeType.BIG_TREE);
    }

    @Test
    void jungleGrowsTheMegaVariant() {
        assertThat(BigTreeCommand.resolve("jungle")).contains(TreeType.JUNGLE);
    }

    @Test
    void redwoodFamilyGrowsMegaRedwood() {
        assertThat(BigTreeCommand.resolve("redwood")).contains(TreeType.MEGA_REDWOOD);
        assertThat(BigTreeCommand.resolve("spruce")).contains(TreeType.MEGA_REDWOOD);
        assertThat(BigTreeCommand.resolve("mega_redwood")).contains(TreeType.MEGA_REDWOOD);
    }

    @Test
    void darkOakIsForgivingAboutUnderscores() {
        assertThat(BigTreeCommand.resolve("darkoak")).contains(TreeType.DARK_OAK);
        assertThat(BigTreeCommand.resolve("dark_oak")).contains(TreeType.DARK_OAK);
    }

    @Test
    void unknownTypeIsEmpty() {
        assertThat(BigTreeCommand.resolve("bogus")).isEmpty();
    }
}
