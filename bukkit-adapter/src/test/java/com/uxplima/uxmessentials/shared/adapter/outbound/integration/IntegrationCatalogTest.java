package com.uxplima.uxmessentials.shared.adapter.outbound.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The catalog's own consistency. It is data, so what is worth asserting is that the data is well-formed: one
 * entry per plugin, every field populated, and every family represented by at least one entry (a family with
 * no members is a leftover from a retired integration).
 *
 * <p>What the catalog says about the outside world (that each entry is declared in the manifest and that its
 * seam file exists) is checked by {@code IntegrationCatalogDriftTest}, which reads the shipped resources.
 */
class IntegrationCatalogTest {

    @Test
    void everyPluginAppearsExactlyOnce() {
        assertThat(IntegrationCatalog.plugins())
                .as("one entry per plugin: a plugin integrated in two families is still one soft-depend")
                .doesNotHaveDuplicates();
    }

    @Test
    void everyEntryIsFullyPopulated() {
        for (Integration integration : IntegrationCatalog.all()) {
            assertThat(integration.plugin()).isNotBlank();
            assertThat(integration.seam()).endsWith(".java");
            assertThat(integration.purpose()).isNotBlank();
        }
    }

    @Test
    void everyFamilyHasAtLeastOneMember() {
        for (IntegrationFamily family : IntegrationFamily.values()) {
            assertThat(IntegrationCatalog.byFamily().get(family))
                    .as("family %s has no members: retire the family or catalog its integration", family)
                    .isNotEmpty();
        }
    }

    @Test
    void familiesAreGroupedInDeclarationOrder() {
        assertThat(List.copyOf(IntegrationCatalog.byFamily().keySet())).containsExactly(IntegrationFamily.values());
    }

    @Test
    void theCatalogCoversTheIntegrationsWeShip() {
        // Anchors, not an inventory: the exhaustive check against the manifest is the drift guard's job.
        assertThat(IntegrationCatalog.plugins())
                .contains("Vault", "PlaceholderAPI", "LuckPerms", "Lands", "floodgate", "MiniPlaceholders")
                .hasSizeGreaterThan(30);
    }

    @Test
    void aPluginWeDoNotIntegrateWithIsNotFound() {
        assertThat(IntegrationCatalog.byPlugin("GhostPlugin")).isEmpty();
        assertThat(IntegrationCatalog.byPlugin("Lands")).isPresent();
    }
}
