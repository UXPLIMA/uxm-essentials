package com.uxplima.uxmessentials.scoreboard.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class DisplayContentTest {

    @Test
    void inertIsTheBlankDoNothingDefault() {
        DisplayContent inert = DisplayContent.inert();

        assertThat(inert.title()).isEmpty();
        assertThat(inert.lines()).isEmpty();
        assertThat(inert.worldBlacklist()).isEmpty();
        assertThat(inert.isBlank()).isTrue();
        // Hiding the red score numbers is the modern default, on even when nothing is authored.
        assertThat(inert.hideScoreNumbers()).isTrue();
        // A positive cadence so the render timer never busy-spins even with nothing authored.
        assertThat(inert.refreshInterval()).isPositive();
    }

    @Test
    void carriesTheHideScoreNumbersFlagThrough() {
        DisplayContent shown = new DisplayContent(
                Optional.of("<gold>Server"), List.of("<white>line"), false, Duration.ofSeconds(1L), Set.of());

        assertThat(shown.hideScoreNumbers()).isFalse();
        assertThat(shown.isBlank()).isFalse();
    }

    @Test
    void acceptsUpToTheSidebarLineLimit() {
        List<String> max = IntStream.range(0, DisplayContent.MAX_LINES)
                .mapToObj(i -> "<white>line " + i)
                .collect(Collectors.toList());

        DisplayContent content = new DisplayContent(
                Optional.of("<gold>Server"), max, true, Duration.ofSeconds(2L), Set.of("world_nether"));

        assertThat(content.lines()).hasSize(DisplayContent.MAX_LINES);
        assertThat(content.isBlank()).isFalse();
    }

    @Test
    void acceptsMoreCandidatesThanTheVisibleSidebarLimit() {
        List<String> tooMany = IntStream.range(0, DisplayContent.MAX_LINES + 1)
                .mapToObj(i -> "line " + i)
                .collect(Collectors.toList());

        DisplayContent content = new DisplayContent(Optional.empty(), tooMany, true, Duration.ofSeconds(1L), Set.of());

        assertThat(content.lines()).hasSize(DisplayContent.MAX_LINES + 1);
        assertThat(content.lineDefinitions()).extracting(SidebarLine::id).doesNotHaveDuplicates();
    }

    @Test
    void rejectsANonPositiveRefreshInterval() {
        assertThatThrownBy(() -> content(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> content(Duration.ofSeconds(-1L))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void worldBlacklistMembershipIsQueryable() {
        DisplayContent content =
                new DisplayContent(Optional.empty(), List.of(), true, Duration.ofSeconds(1L), Set.of("world_the_end"));

        assertThat(content.suppressedIn("world_the_end")).isTrue();
        assertThat(content.suppressedIn("WORLD_THE_END")).isTrue();
        assertThat(content.suppressedIn("world")).isFalse();
    }

    @Test
    void copiesItsCollectionsSoLaterMutationDoesNotLeakIn() {
        List<String> mutableLines = new java.util.ArrayList<>(List.of("<white>one"));
        DisplayContent content =
                new DisplayContent(Optional.empty(), mutableLines, true, Duration.ofSeconds(1L), Set.of());

        mutableLines.add("<white>two");
        assertThat(content.lines()).containsExactly("<white>one");
        assertThatThrownBy(() -> content.lines().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    private static DisplayContent content(Duration interval) {
        return new DisplayContent(Optional.empty(), List.of(), true, interval, Collections.emptySet());
    }
}
