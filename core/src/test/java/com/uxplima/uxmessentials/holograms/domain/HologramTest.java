package com.uxplima.uxmessentials.holograms.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

class HologramTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    private static Hologram twoLine() {
        return Hologram.create(
                HologramName.of("spawn"),
                AT,
                List.of(new HologramLine("one"), new HologramLine("two")),
                Instant.ofEpochMilli(1_000));
    }

    @Test
    void rejectsAnEmptyLineList() {
        assertThatThrownBy(() -> Hologram.create(HologramName.of("x"), AT, List.of(), Instant.ofEpochMilli(1_000)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void movedToKeepsNameLinesAndCreationTime() {
        Hologram moved = twoLine().movedTo(Position.of(WORLD, 9, 70, 9));

        assertThat(moved.name().value()).isEqualTo("spawn");
        assertThat(moved.lines()).hasSize(2);
        assertThat(moved.createdAt()).isEqualTo(Instant.ofEpochMilli(1_000));
        assertThat(moved.location().blockX()).isEqualTo(9);
    }

    @Test
    void withLineAppendedAddsAtTheEnd() {
        Hologram three = twoLine().withLineAppended(new HologramLine("three"));

        assertThat(three.lines()).map(HologramLine::value).containsExactly("one", "two", "three");
    }

    @Test
    void withLineReplacedSwapsTheLine() {
        Hologram edited = twoLine().withLineReplaced(0, new HologramLine("first"));

        assertThat(edited.lines()).map(HologramLine::value).containsExactly("first", "two");
    }

    @Test
    void withLineReplacedRejectsAnOutOfRangeIndex() {
        assertThatThrownBy(() -> twoLine().withLineReplaced(5, new HologramLine("x")))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void withLineRemovedDropsTheLine() {
        Hologram one = twoLine().withLineRemoved(0);

        assertThat(one.lines()).map(HologramLine::value).containsExactly("two");
    }

    @Test
    void withLineRemovedRefusesToEmptyTheHologram() {
        Hologram one = twoLine().withLineRemoved(0);

        assertThatThrownBy(() -> one.withLineRemoved(0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hologramLineRejectsBlankText() {
        assertThatThrownBy(() -> new HologramLine("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
