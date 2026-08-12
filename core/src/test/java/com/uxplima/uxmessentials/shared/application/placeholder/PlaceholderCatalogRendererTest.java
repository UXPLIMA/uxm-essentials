package com.uxplima.uxmessentials.shared.application.placeholder;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** What {@code /uxmess placeholders} prints, and what its export writes. */
class PlaceholderCatalogRendererTest {

    @Test
    void theOpeningScreenNamesEveryAreaWithACount() {
        assertThat(PlaceholderCatalogRenderer.areas())
                .hasSize(PlaceholderCatalog.areas().size())
                .anySatisfy(line -> assertThat(line).startsWith("shared: "))
                .allSatisfy(line -> assertThat(line).contains("key"));
    }

    @Test
    void aPageCarriesAtMostAPageful() {
        PlaceholderCatalogRenderer.Page page = PlaceholderCatalogRenderer.page("homes", 1);

        assertThat(page.empty()).isFalse();
        assertThat(page.lines()).hasSizeLessThanOrEqualTo(PlaceholderCatalogRenderer.PAGE_SIZE);
        assertThat(page.number()).isOne();
        assertThat(page.of()).isPositive();
    }

    @Test
    void everyPageOfAnAreaTogetherIsTheWholeArea() {
        int seen = 0;
        int pages = PlaceholderCatalogRenderer.page("playerstate", 1).of();
        for (int number = 1; number <= pages; number++) {
            seen += PlaceholderCatalogRenderer.page("playerstate", number)
                    .lines()
                    .size();
        }

        assertThat(seen).isEqualTo(PlaceholderCatalog.forArea("playerstate").size());
    }

    @Test
    void aPageNumberPastTheEndShowsTheLastPageRatherThanNothing() {
        PlaceholderCatalogRenderer.Page page = PlaceholderCatalogRenderer.page("homes", 9999);

        assertThat(page.empty()).isFalse();
        assertThat(page.number()).isEqualTo(page.of());
    }

    @Test
    void anAreaThatDoesNotExistIsEmptyAndGetsASuggestion() {
        assertThat(PlaceholderCatalogRenderer.page("hom", 1).empty()).isTrue();
        assertThat(PlaceholderCatalogRenderer.suggest("hom")).contains("homes");
        assertThat(PlaceholderCatalogRenderer.suggest("zzzz")).isEmpty();
    }

    @Test
    void aLineShowsTheKeyAsAnOperatorWouldTypeIt() {
        PlaceholderSpec spec = PlaceholderCatalog.find("homes_count").orElseThrow();

        assertThat(PlaceholderCatalogRenderer.line(spec))
                .startsWith("%uxmessentials_homes_count%")
                .contains("[player]");
    }

    @Test
    void theExportCarriesEveryKeyInATable() {
        String markdown = PlaceholderCatalogRenderer.markdown();

        assertThat(markdown).startsWith("# uxmEssentials placeholders");
        for (PlaceholderSpec spec : PlaceholderCatalog.all()) {
            assertThat(markdown)
                    .describedAs("the export is missing %s", spec.key())
                    .contains("`" + spec.placeholder() + "`");
        }
    }

    @Test
    void aDescriptionWithAPipeCannotBreakTheExportTable() {
        assertThat(PlaceholderCatalogRenderer.markdown().lines())
                .filteredOn(line -> line.startsWith("| `%"))
                .isNotEmpty()
                .allSatisfy(line -> assertThat(line.replace("\\|", "")
                                .chars()
                                .filter(character -> character == '|')
                                .count())
                        .describedAs("this row has a column too many or too few: %s", line)
                        .isEqualTo(4L));
    }
}
