package com.uxplima.uxmessentials.shared.application.placeholder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * The catalogue as a value: what it promises about every key, and how a key is looked up in it.
 *
 * <p>The drift guard in the adapter resolves the catalogue against the running resolver. This checks the catalogue
 * against itself, which is the cheaper half and the one that fails first when a row is written wrong.
 */
class PlaceholderCatalogTest {

    @Test
    void everyEntryCarriesADescriptionAnOperatorCanActOn() {
        assertThat(PlaceholderCatalog.all()).isNotEmpty().allSatisfy(spec -> {
            assertThat(spec.description()).isNotBlank();
            assertThat(spec.description().length())
                    .describedAs("%s has a description too short to mean anything", spec.key())
                    .isGreaterThan(10);
        });
    }

    @Test
    void aFamilyShowsItsSegmentAndAFixedKeyDoesNot() {
        assertThat(PlaceholderCatalog.all())
                .allSatisfy(spec -> assertThat(spec.key().contains("<"))
                        .describedAs("%s is a %s", spec.key(), spec.shape())
                        .isEqualTo(spec.shape() == PlaceholderShape.FAMILY));
    }

    @Test
    void everyKeyReadsAsAPlaceholderAnOperatorCanPaste() {
        assertThat(PlaceholderCatalog.all())
                .allSatisfy(spec -> assertThat(spec.placeholder())
                        .startsWith("%uxmessentials_")
                        .endsWith("%"));
    }

    @Test
    void areasStartWithTheKernelAndThenReadAlphabetically() {
        List<String> areas = PlaceholderCatalog.areas();

        assertThat(areas).first().isEqualTo("shared");
        assertThat(areas.subList(1, areas.size())).isSorted();
        assertThat(areas).doesNotHaveDuplicates();
    }

    @Test
    void everyEntryIsReachableThroughItsArea() {
        int throughAreas = PlaceholderCatalog.areas().stream()
                .mapToInt(area -> PlaceholderCatalog.forArea(area).size())
                .sum();

        assertThat(throughAreas).isEqualTo(PlaceholderCatalog.all().size());
    }

    @Test
    void aFixedKeyIsFoundByItsOwnName() {
        assertThat(PlaceholderCatalog.find("homes_count"))
                .get()
                .satisfies(spec -> assertThat(spec.shape()).isEqualTo(PlaceholderShape.FIXED));
    }

    @Test
    void aMemberOfAFamilyIsFoundThroughTheFamily() {
        assertThat(PlaceholderCatalog.find("kit_cost_starter"))
                .get()
                .satisfies(spec -> assertThat(spec.key()).isEqualTo("kit_cost_<kit>"));
    }

    @Test
    void theLongestFamilyWinsWhenTwoCouldMatch() {
        // economy_balance_<currency> and economy_balance_formatted_<currency> share a head as far as "balance_".
        assertThat(PlaceholderCatalog.find("economy_balance_formatted_gems"))
                .get()
                .satisfies(spec -> assertThat(spec.key()).isEqualTo("economy_balance_formatted_<currency>"));
    }

    @Test
    void aFamilyIsSampledIntoSomethingResolvable() {
        PlaceholderSpec baltop = PlaceholderCatalog.all().stream()
                .filter(spec -> spec.key().equals("economy_baltop_<currency>_<n>_name"))
                .findFirst()
                .orElseThrow();

        assertThat(baltop.sampled("gems")).isEqualTo("economy_baltop_gems_1_name");
        assertThat(PlaceholderCatalog.find("homes_count").orElseThrow().sampled("gems"))
                .isEqualTo("homes_count");
    }

    @Test
    void somethingOutsideTheCatalogueIsNotFound() {
        assertThat(PlaceholderCatalog.find("nothing_like_this")).isEmpty();
    }

    @Test
    void aRowWrittenWrongIsRefusedAtConstruction() {
        ModuleId homes = ModuleId.of("homes");

        assertThatThrownBy(
                        () -> PlaceholderSpec.of("Homes_Count", "An upper-case key.", PlaceholderScope.PLAYER, homes))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlaceholderSpec.of("homes_count", "  ", PlaceholderScope.PLAYER, homes))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlaceholderSpec.of(
                        "homes_exists_<home>", "A family called fixed.", PlaceholderScope.PLAYER, homes))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlaceholderSpec.family(
                        "homes_count", "A family with no segment.", PlaceholderScope.PLAYER, homes))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
