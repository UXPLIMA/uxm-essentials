package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * A name typed into a teleport command that matches nobody on this backend is looked up on the others. Bedrock
 * players arrive with a Floodgate prefix and proxies may forward names longer than Java allows, so the name is tried
 * as typed rather than against Java's name rules.
 */
class TypedNameTest {

    @Test
    void aFloodgateNameWithTheDefaultPrefixIsTriedOnTheNetwork() {
        assertThat(TeleportCommandSupport.plainName(".Steve")).contains(".Steve");
    }

    @Test
    void aFloodgateNameWithACustomPrefixIsTriedOnTheNetwork() {
        assertThat(TeleportCommandSupport.plainName("BE_Steve")).contains("BE_Steve");
        assertThat(TeleportCommandSupport.plainName("+Steve")).contains("+Steve");
        assertThat(TeleportCommandSupport.plainName("*Steve")).contains("*Steve");
    }

    @Test
    void aNameTypedInQuotesIsTriedWithoutTheQuotes() {
        assertThat(TeleportCommandSupport.plainName("\"*Steve\"")).contains("*Steve");
        assertThat(TeleportCommandSupport.plainName("'!Alex'")).contains("!Alex");
    }

    @Test
    void aNameLongerThanSixteenCharactersIsTriedOnTheNetwork() {
        assertThat(TeleportCommandSupport.plainName(".AVeryLongGamertag")).contains(".AVeryLongGamertag");
    }

    @Test
    void aSelectorOrAUuidIsNotANameOnAnotherBackend() {
        assertThat(TeleportCommandSupport.plainName("@p")).isEmpty();
        assertThat(TeleportCommandSupport.plainName("@a[limit=1]")).isEmpty();
        assertThat(TeleportCommandSupport.plainName("069a79f4-44e9-4726-a5be-fca90e38aaf5"))
                .isEmpty();
        assertThat(TeleportCommandSupport.plainName("\"unterminated")).isEmpty();
    }
}
