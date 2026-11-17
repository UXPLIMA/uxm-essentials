package com.uxplima.uxmessentials.communication.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A template is filled in one pass: a value is put in as it is, never filled again, and unknown tokens stay. */
class PlaceholderBindingsTest {

    @Test
    void aValueHoldingAnotherTokenIsNotFilledAgain() {
        PlaceholderBindings bindings =
                PlaceholderBindings.empty().with("displayname", "{player}").with("player", "Steve");

        assertThat(bindings.apply("{displayname} joined as {player}")).isEqualTo("{player} joined as Steve");
    }

    @Test
    void aValueHoldingItsOwnTokenIsPutInOnce() {
        PlaceholderBindings bindings = PlaceholderBindings.empty().with("player", "{player}{player}");

        assertThat(bindings.apply("hi {player}")).isEqualTo("hi {player}{player}");
    }

    @Test
    void anUnknownTokenAndAStrayBraceAreLeftAsWritten() {
        PlaceholderBindings bindings = PlaceholderBindings.empty().with("player", "Steve");

        assertThat(bindings.apply("{nope} {a{player} {player")).isEqualTo("{nope} {aSteve {player");
    }
}
