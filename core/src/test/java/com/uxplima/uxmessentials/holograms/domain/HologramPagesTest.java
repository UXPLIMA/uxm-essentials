package com.uxplima.uxmessentials.holograms.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/** The multi-page hologram model: the {@link HologramPage} value object and the {@link Hologram} page transitions. */
class HologramPagesTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    @Test
    void aPageRejectsAnEmptyLineList() {
        assertThatThrownBy(() -> HologramPage.of(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aFreshHologramIsSinglePage() {
        Hologram hologram = text("spawn", "one", "two");

        assertThat(hologram.isMultiPage()).isFalse();
        assertThat(hologram.pageCount()).isEqualTo(1);
        assertThat(hologram.pages()).isNull();
        assertThat(hologram.maxPageLineCount()).isEqualTo(2);
        assertThat(hologram.pageLines(0)).map(HologramLine::value).containsExactly("one", "two");
    }

    @Test
    void appendingAPagePromotesToMultiPageWithTheCurrentLinesAsPageZero() {
        Hologram hologram = text("spawn", "p0").withPageAppended(HologramPage.of(lines("p1-a", "p1-b")));

        assertThat(hologram.isMultiPage()).isTrue();
        assertThat(hologram.pageCount()).isEqualTo(2);
        assertThat(hologram.pageLines(0)).map(HologramLine::value).containsExactly("p0");
        assertThat(hologram.pageLines(1)).map(HologramLine::value).containsExactly("p1-a", "p1-b");
        // Page 0 stays the rendered content, so the base spawn keeps showing the first page.
        assertThat(hologram.lines()).map(HologramLine::value).containsExactly("p0");
        assertThat(hologram.maxPageLineCount()).isEqualTo(2);
    }

    @Test
    void removingAPageDownToOneCollapsesBackToSinglePage() {
        Hologram twoPage = text("spawn", "p0").withPageAppended(HologramPage.of(lines("p1")));

        Hologram collapsed = twoPage.withPageRemoved(1);

        assertThat(collapsed.isMultiPage()).isFalse();
        assertThat(collapsed.pageCount()).isEqualTo(1);
        assertThat(collapsed.lines()).map(HologramLine::value).containsExactly("p0");
    }

    @Test
    void removingPageZeroPromotesTheNextPageToTheRenderedContent() {
        Hologram threePage = text("spawn", "p0")
                .withPageAppended(HologramPage.of(lines("p1")))
                .withPageAppended(HologramPage.of(lines("p2")));

        Hologram afterRemove = threePage.withPageRemoved(0);

        assertThat(afterRemove.pageCount()).isEqualTo(2);
        assertThat(afterRemove.pageLines(0)).map(HologramLine::value).containsExactly("p1");
        assertThat(afterRemove.lines()).map(HologramLine::value).containsExactly("p1");
    }

    @Test
    void aLineEditFlowsIntoPageZeroSoItSurvivesAReload() {
        Hologram twoPage = text("spawn", "p0").withPageAppended(HologramPage.of(lines("p1")));

        Hologram edited = twoPage.withLineAppended(new HologramLine("p0-extra"));

        // Page 0 tracks the content lines; the second page is untouched.
        assertThat(edited.pageLines(0)).map(HologramLine::value).containsExactly("p0", "p0-extra");
        assertThat(edited.pageLines(1)).map(HologramLine::value).containsExactly("p1");
    }

    @Test
    void removingAPageOfASinglePageHologramIsRejected() {
        Hologram hologram = text("spawn", "only");

        assertThatThrownBy(() -> hologram.withPageRemoved(0)).isInstanceOf(IllegalStateException.class);
    }

    private static Hologram text(String name, String... lines) {
        return Hologram.create(HologramName.of(name), Position.of(WORLD, 0, 64, 0), lines(lines), Instant.EPOCH);
    }

    private static List<HologramLine> lines(String... values) {
        return List.of(values).stream().map(HologramLine::new).toList();
    }
}
