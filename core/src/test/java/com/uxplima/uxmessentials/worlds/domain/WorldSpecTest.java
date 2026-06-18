package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class WorldSpecTest {

    @Test
    void normalFactoryDefaultsToVanillaNormalWithStructures() {
        WorldSpec spec = WorldSpec.normal();
        assertThat(spec.environment()).isEqualTo(WorldEnvironment.NORMAL);
        assertThat(spec.worldType()).isEqualTo(WorldGenType.NORMAL);
        assertThat(spec.seed()).isEmpty();
        assertThat(spec.generator()).isEmpty();
        assertThat(spec.generateStructures()).isTrue();
        assertThat(spec.dimension()).isEmpty();
    }

    @Test
    void carriesSeedAndGenerator() {
        WorldSpec spec = new WorldSpec(
                WorldEnvironment.NETHER,
                WorldGenType.FLAT,
                Optional.of(42L),
                Optional.of(GeneratorRef.of("VoidGen")),
                false,
                Optional.empty());
        assertThat(spec.seed()).hasValue(42L);
        assertThat(spec.generator()).map(GeneratorRef::value).hasValue("VoidGen");
        assertThat(spec.generateStructures()).isFalse();
    }

    @Test
    void generatorRefAndDimensionRejectBlank() {
        assertThatThrownBy(() -> GeneratorRef.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DimensionKey.of("nocolon")).isInstanceOf(IllegalArgumentException.class);
        assertThat(DimensionKey.of("myplugin:void").value()).isEqualTo("myplugin:void");
    }
}
