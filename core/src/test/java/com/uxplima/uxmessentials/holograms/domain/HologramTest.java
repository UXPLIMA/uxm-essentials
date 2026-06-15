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

    @Test
    void createDefaultsToTheDefaultAppearanceAndStaticInterval() {
        Hologram hologram = twoLine();

        assertThat(hologram.appearance()).isEqualTo(Appearance.defaults());
        assertThat(hologram.visibility()).isEqualTo(Visibility.everyone());
        assertThat(hologram.refreshIntervalTicks()).isZero();
        assertThat(hologram.refreshes()).isFalse();
    }

    @Test
    void withVisibilityKeepsEverythingElseButRetargets() {
        Visibility restricted =
                Visibility.restrictedTo("uxmessentials.hologram.see.vip").withDistance(48);

        Hologram gated = twoLine().withRefreshIntervalTicks(40).withVisibility(restricted);

        assertThat(gated.visibility()).isEqualTo(restricted);
        assertThat(gated.refreshIntervalTicks()).isEqualTo(40);
        assertThat(gated.lines()).hasSize(2);
        assertThat(gated.appearance()).isEqualTo(Appearance.defaults());
        assertThat(gated.name().value()).isEqualTo("spawn");
    }

    @Test
    void withAppearanceKeepsNameLinesAndIntervalButRestyles() {
        Appearance styled = Appearance.defaults().withBillboard(Billboard.FIXED).withScale(2.0f);

        Hologram restyled = twoLine().withRefreshIntervalTicks(40).withAppearance(styled);

        assertThat(restyled.appearance()).isEqualTo(styled);
        assertThat(restyled.refreshIntervalTicks()).isEqualTo(40);
        assertThat(restyled.lines()).hasSize(2);
        assertThat(restyled.name().value()).isEqualTo("spawn");
    }

    @Test
    void withRefreshIntervalMarksTheHologramRefreshing() {
        Hologram refreshing = twoLine().withRefreshIntervalTicks(20);

        assertThat(refreshing.refreshes()).isTrue();
        assertThat(refreshing.refreshIntervalTicks()).isEqualTo(20);
    }

    @Test
    void withRefreshIntervalRejectsANegativeValue() {
        assertThatThrownBy(() -> twoLine().withRefreshIntervalTicks(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
