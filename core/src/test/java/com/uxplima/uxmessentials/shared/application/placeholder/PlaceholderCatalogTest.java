package com.uxplima.uxmessentials.shared.application.placeholder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

class PlaceholderCatalogTest {

    @Test
    void theCatalogueLoadsAndAnswersAFamilyByOneOfItsMembers() {
        assertThat(PlaceholderCatalog.find("regions_allowed_break"))
                .map(PlaceholderSpec::key)
                .contains("regions_allowed_<action>");
    }

    @Test
    void everyKeyCarriesAnExampleOfWhatItRenders() {
        assertThat(PlaceholderCatalog.all())
                .allSatisfy(spec -> assertThat(spec.example()).isNotBlank());
    }

    @Test
    void aKeyWithoutAnExampleIsRefused() {
        assertThatThrownBy(() -> PlaceholderSpec.of(
                        "homes_count",
                        "How many homes the player has set.",
                        " ",
                        PlaceholderScope.PLAYER,
                        ModuleId.of("homes")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
