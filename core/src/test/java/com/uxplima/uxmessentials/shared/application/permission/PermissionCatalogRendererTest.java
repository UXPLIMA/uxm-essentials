package com.uxplima.uxmessentials.shared.application.permission;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** What {@code /uxmess permissions} prints, and what its export writes. */
class PermissionCatalogRendererTest {

    @Test
    void theOpeningScreenNamesEveryAreaWithACount() {
        assertThat(PermissionCatalogRenderer.areas())
                .hasSize(PermissionCatalog.areas().size())
                .anySatisfy(line -> assertThat(line).startsWith("shared: "))
                .allSatisfy(line -> assertThat(line).contains("node"));
    }

    @Test
    void aPageCarriesAtMostAPageful() {
        PermissionCatalogRenderer.Page page = PermissionCatalogRenderer.page("homes", 1);

        assertThat(page.empty()).isFalse();
        assertThat(page.lines()).hasSizeLessThanOrEqualTo(PermissionCatalogRenderer.PAGE_SIZE);
        assertThat(page.number()).isOne();
        assertThat(page.of()).isPositive();
    }

    @Test
    void everyPageOfAnAreaTogetherIsTheWholeArea() {
        int seen = 0;
        int pages = PermissionCatalogRenderer.page("itemworld", 1).of();
        for (int number = 1; number <= pages; number++) {
            seen += PermissionCatalogRenderer.page("itemworld", number).lines().size();
        }

        assertThat(seen).isEqualTo(PermissionCatalog.forArea("itemworld").size());
    }

    @Test
    void aPageNumberPastTheEndShowsTheLastPageRatherThanNothing() {
        PermissionCatalogRenderer.Page page = PermissionCatalogRenderer.page("homes", 9999);

        assertThat(page.empty()).isFalse();
        assertThat(page.number()).isEqualTo(page.of());
    }

    @Test
    void anAreaThatDoesNotExistIsEmptyAndGetsASuggestion() {
        assertThat(PermissionCatalogRenderer.page("hom", 1).empty()).isTrue();
        assertThat(PermissionCatalogRenderer.suggest("hom")).contains("homes");
        assertThat(PermissionCatalogRenderer.suggest("zzzz")).isEmpty();
    }

    @Test
    void aFamilyShowsItsShapeRatherThanABooleanDefault() {
        PermissionSpec family = PermissionCatalog.all().stream()
                .filter(spec -> spec.node().equals("uxmessentials.home.limit.<n>"))
                .findFirst()
                .orElseThrow();

        // A quota node is held for its number; calling it "true" or "op" would say the wrong thing about it.
        assertThat(PermissionCatalogRenderer.line(family)).contains("[quota]");
        assertThat(PermissionCatalogRenderer.line(
                        PermissionCatalog.find("uxmessentials.admin").orElseThrow()))
                .contains("[op]");
    }

    @Test
    void theExportCarriesEveryNodeInATable() {
        String markdown = PermissionCatalogRenderer.markdown();

        assertThat(markdown).startsWith("# uxmEssentials permissions");
        for (PermissionSpec spec : PermissionCatalog.all()) {
            assertThat(markdown)
                    .describedAs("the export is missing %s", spec.node())
                    .contains("`" + spec.node() + "`");
        }
    }

    @Test
    void aDescriptionWithAPipeCannotBreakTheExportTable() {
        // Command syntax is full of pipes ("/time <set|add>"), and a raw one would open a column mid-description.
        assertThat(PermissionCatalogRenderer.markdown().lines())
                .filteredOn(line -> line.startsWith("| `"))
                .isNotEmpty()
                .allSatisfy(line -> assertThat(line.replace("\\|", "")
                                .chars()
                                .filter(character -> character == '|')
                                .count())
                        .describedAs("this row has a column too many or too few: %s", line)
                        .isEqualTo(4L));
    }
}
