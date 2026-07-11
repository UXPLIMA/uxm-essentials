package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The generic paged-result value: {@link Page#hasNext()} boundaries, defensive copying, and input guards. */
class PageTest {

    @Test
    void hasNextIsTrueWhileLaterMatchesRemainBeyondThisPage() {
        // 25 matches, 10 per page → pages 0 and 1 have a successor, page 2 (the partial last page) does not.
        assertThat(new Page<>(List.of("a"), 25L, 0, 10).hasNext()).isTrue();
        assertThat(new Page<>(List.of("a"), 25L, 1, 10).hasNext()).isTrue();
        assertThat(new Page<>(List.of("a"), 25L, 2, 10).hasNext()).isFalse();
    }

    @Test
    void hasNextIsFalseOnAFullFinalPageAndAnEmptyResult() {
        assertThat(new Page<>(List.of("a"), 10L, 0, 10).hasNext()).isFalse();
        assertThat(new Page<>(List.of("a"), 5L, 0, 10).hasNext()).isFalse();
        assertThat(Page.empty(0, 10).hasNext()).isFalse();
        assertThat(Page.empty(0, 10).isEmpty()).isTrue();
    }

    @Test
    void itemsAreCopiedDefensivelySoACallerCannotMutateAPage() {
        List<String> source = new ArrayList<>(List.of("a", "b"));
        Page<String> page = new Page<>(source, 2L, 0, 10);

        source.add("c");

        assertThat(page.items()).containsExactly("a", "b");
        assertThatThrownBy(() -> page.items().add("z")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void invalidCoordinatesAreRejected() {
        assertThatThrownBy(() -> new Page<>(List.of(), -1L, 0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Page<>(List.of(), 0L, -1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Page<>(List.of(), 0L, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
