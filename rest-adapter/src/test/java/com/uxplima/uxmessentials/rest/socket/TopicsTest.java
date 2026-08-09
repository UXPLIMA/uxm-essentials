package com.uxplima.uxmessentials.rest.socket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TopicsTest {

    /** A connection nobody subscribed on hears nothing, which is the whole of the default. */
    @Test
    void aFreshConnectionWantsNothing() {
        assertThat(new Topics().wants("economy.wallet-credit")).isFalse();
    }

    @Test
    void anExactNameMatchesOnlyItself() {
        Topics topics = new Topics();
        topics.add(List.of("economy.wallet-credit"));

        assertThat(topics.wants("economy.wallet-credit")).isTrue();
        assertThat(topics.wants("economy.wallet-debit")).isFalse();
    }

    @Test
    void aStarAfterAContextTakesTheWholeContext() {
        Topics topics = new Topics();
        topics.add(List.of("economy.*"));

        assertThat(topics.wants("economy.wallet-credit")).isTrue();
        assertThat(topics.wants("economy.bank-deposit")).isTrue();
        assertThat(topics.wants("home.create")).isFalse();
    }

    @Test
    void aStarOnItsOwnTakesEverything() {
        Topics topics = new Topics();
        topics.add(List.of("*"));

        assertThat(topics.wants("anything.at-all")).isTrue();
    }

    @Test
    void unsubscribingRemovesThePatternAsItWasWritten() {
        Topics topics = new Topics();
        topics.add(List.of("economy.*", "home.create"));
        topics.remove(List.of("economy.*"));

        assertThat(topics.wants("economy.wallet-credit")).isFalse();
        assertThat(topics.wants("home.create")).isTrue();
        assertThat(topics.current()).containsExactly("home.create");
    }

    @Test
    void surroundingSpaceIsNotPartOfAName() {
        Topics topics = new Topics();
        topics.add(List.of("  home.create  ", "", "   "));

        assertThat(topics.current()).containsExactly("home.create");
    }
}
