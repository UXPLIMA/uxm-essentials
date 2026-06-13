package com.uxplima.uxmessentials.tablist.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TablistContentTest {

    @Test
    void inertIsTheBlankDoNothingDefault() {
        TablistContent inert = TablistContent.inert();

        assertThat(inert.header()).isEmpty();
        assertThat(inert.footer()).isEmpty();
        assertThat(inert.worldBlacklist()).isEmpty();
        assertThat(inert.isBlank()).isTrue();
        // A positive cadence so the render timer never busy-spins even with nothing authored.
        assertThat(inert.refreshInterval()).isPositive();
    }

    @Test
    void rejectsANonPositiveRefreshInterval() {
        assertThatThrownBy(() -> content(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> content(Duration.ofSeconds(-1L))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void worldBlacklistMembershipIsQueryable() {
        TablistContent content =
                new TablistContent(List.of(), List.of(), Duration.ofSeconds(1L), Set.of("world_the_end"));

        assertThat(content.suppressedIn("world_the_end")).isTrue();
        assertThat(content.suppressedIn("world")).isFalse();
    }

    @Test
    void copiesItsCollectionsSoLaterMutationDoesNotLeakIn() {
        List<String> mutableHeader = new java.util.ArrayList<>(List.of("<gray>one"));
        TablistContent content = new TablistContent(mutableHeader, List.of(), Duration.ofSeconds(1L), Set.of());

        mutableHeader.add("<gray>two");
        assertThat(content.header()).containsExactly("<gray>one");
        assertThatThrownBy(() -> content.header().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void isBlankIsTrueOnlyWhenBothHeaderAndFooterAreEmpty() {
        assertThat(new TablistContent(List.of(), List.of(), Duration.ofSeconds(1L), Set.of()).isBlank())
                .isTrue();
        assertThat(new TablistContent(List.of("<gray>Welcome"), List.of(), Duration.ofSeconds(1L), Set.of()).isBlank())
                .isFalse();
        assertThat(new TablistContent(List.of(), List.of("<gray>play.example.net"), Duration.ofSeconds(1L), Set.of())
                        .isBlank())
                .isFalse();
    }

    private static TablistContent content(Duration interval) {
        return new TablistContent(List.of(), List.of(), interval, Collections.emptySet());
    }
}
