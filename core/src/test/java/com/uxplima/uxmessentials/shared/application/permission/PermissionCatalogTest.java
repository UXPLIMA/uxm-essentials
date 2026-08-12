package com.uxplima.uxmessentials.shared.application.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * The catalogue as a value: what it promises about every row, and how a node is looked up in it.
 *
 * <p>The drift guard in the adapter checks the catalogue against the running code. This checks the catalogue against
 * itself, which is the cheaper half and the one that fails first when a row is written wrong.
 */
class PermissionCatalogTest {

    @Test
    void everyEntryBelongsToTheUxmessentialsSpace() {
        assertThat(PermissionCatalog.all())
                .isNotEmpty()
                .allSatisfy(spec -> assertThat(spec.node()).startsWith("uxmessentials."));
    }

    @Test
    void everyEntryCarriesADescriptionAnOperatorCanAct0n() {
        assertThat(PermissionCatalog.all()).allSatisfy(spec -> {
            assertThat(spec.description()).isNotBlank();
            assertThat(spec.description().length())
                    .describedAs("%s has a description too short to mean anything", spec.node())
                    .isGreaterThan(10);
        });
    }

    @Test
    void aFamilyShowsItsPlaceholderAndAFixedNodeDoesNot() {
        assertThat(PermissionCatalog.all())
                .allSatisfy(spec -> assertThat(spec.node().contains("<"))
                        .describedAs("%s is a %s", spec.node(), spec.shape())
                        .isEqualTo(!spec.registrable()));
    }

    @Test
    void onlyFixedNodesAreHandedToTheServer() {
        assertThat(PermissionCatalog.registrable())
                .isNotEmpty()
                .allSatisfy(spec -> assertThat(spec.shape()).isEqualTo(PermissionShape.FIXED));
        assertThat(PermissionCatalog.registrable())
                .hasSizeLessThan(PermissionCatalog.all().size());
    }

    @Test
    void areasStartWithTheKernelAndThenReadAlphabetically() {
        List<String> areas = PermissionCatalog.areas();

        assertThat(areas).first().isEqualTo("shared");
        assertThat(areas.subList(1, areas.size())).isSorted();
        assertThat(areas).doesNotHaveDuplicates();
    }

    @Test
    void everyEntryIsReachableThroughItsArea() {
        int throughAreas = PermissionCatalog.areas().stream()
                .mapToInt(area -> PermissionCatalog.forArea(area).size())
                .sum();

        assertThat(throughAreas).isEqualTo(PermissionCatalog.all().size());
    }

    @Test
    void aFixedNodeIsFoundByItsOwnName() {
        assertThat(PermissionCatalog.find("uxmessentials.admin"))
                .get()
                .satisfies(spec -> assertThat(spec.registrable()).isTrue());
    }

    @Test
    void aMemberOfAFamilyIsFoundThroughTheFamily() {
        assertThat(PermissionCatalog.find("uxmessentials.home.limit.12"))
                .get()
                .satisfies(spec -> assertThat(spec.node()).isEqualTo("uxmessentials.home.limit.<n>"));
    }

    @Test
    void theHeadOfAFamilyCountsAsTheFamily() {
        // The quota resolver writes the head and appends the number it is looking for, so the head is how the code
        // holds one of these.
        assertThat(PermissionCatalog.find("uxmessentials.home.limit"))
                .get()
                .satisfies(spec -> assertThat(spec.node()).isEqualTo("uxmessentials.home.limit.<n>"));
    }

    @Test
    void theLongestFamilyWinsWhenTwoCouldMatch() {
        assertThat(PermissionCatalog.find("uxmessentials.kit.cooldown.starter.30"))
                .get()
                .satisfies(spec -> assertThat(spec.node()).isEqualTo("uxmessentials.kit.cooldown.<kit>.<seconds>"));
    }

    @Test
    void somethingOutsideTheCatalogueIsNotFound() {
        assertThat(PermissionCatalog.find("uxmessentials.nothing.like.this")).isEmpty();
    }

    @Test
    void aRowWrittenWrongIsRefusedAtConstruction() {
        ModuleId homes = ModuleId.of("homes");

        assertThatThrownBy(
                        () -> PermissionSpec.of("homes.use", "A node outside the space.", PermissionDefault.OP, homes))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PermissionSpec.of("uxmessentials.home.use", "  ", PermissionDefault.OP, homes))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PermissionSpec.of(
                        "uxmessentials.home.limit.<n>", "A family called fixed.", PermissionDefault.OP, homes))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PermissionSpec.family(
                        "uxmessentials.home.limit",
                        "A family with no placeholder.",
                        PermissionDefault.OP,
                        PermissionShape.QUOTA,
                        homes))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
