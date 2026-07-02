package com.uxplima.uxmessentials.teleport.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The production biome-key catalog resolved against the server's biome registry: a known key (bare or namespaced)
 * resolves to the same lower-cased path form a validated candidate carries, an unknown key resolves empty, and the
 * key list the {@code /rtp biome} argument tab-completes is non-empty.
 */
class BukkitBiomeCatalogTest {

    private BukkitBiomeCatalog catalog;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        catalog = new BukkitBiomeCatalog();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void resolvesABareKeyToItsPathName() {
        Optional<BiomeName> plains = catalog.resolve("plains");

        assertThat(plains).contains(BiomeName.of("plains"));
    }

    @Test
    void resolvesANamespacedKeyToo() {
        assertThat(catalog.resolve("minecraft:desert")).contains(BiomeName.of("desert"));
    }

    @Test
    void resolvesCaseInsensitively() {
        assertThat(catalog.resolve("PLAINS")).contains(BiomeName.of("plains"));
    }

    @Test
    void anUnknownKeyResolvesEmpty() {
        assertThat(catalog.resolve("not_a_biome")).isEmpty();
        assertThat(catalog.resolve("")).isEmpty();
    }

    @Test
    void theKeyListIsNonEmptyAndIncludesPlains() {
        assertThat(catalog.keys()).isNotEmpty().contains("plains");
    }
}
