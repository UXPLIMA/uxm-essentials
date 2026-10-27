package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DimensionNamesTest {

    @Test
    void aNameWithoutAColonIsNotADimensionKey() {
        assertThat(DimensionNames.parse("world_nether")).isEmpty();
    }

    @Test
    void aNamespacedKeyParses() {
        assertThat(DimensionNames.parse("mypack:mining")).contains(DimensionKey.of("mypack:mining"));
    }

    @Test
    void aMalformedKeyDoesNotParse() {
        assertThat(DimensionNames.parse("MyPack:Mining")).isEmpty();
        assertThat(DimensionNames.parse(":mining")).isEmpty();
        assertThat(DimensionNames.parse("mypack:")).isEmpty();
    }

    @Test
    void theLevelsThreeDimensionsTakeTheServersNames() {
        assertThat(DimensionNames.worldName(DimensionKey.of("minecraft:overworld"), "world"))
                .isEqualTo("world");
        assertThat(DimensionNames.worldName(DimensionKey.of("minecraft:the_nether"), "world"))
                .isEqualTo("world_nether");
        assertThat(DimensionNames.worldName(DimensionKey.of("minecraft:the_end"), "world"))
                .isEqualTo("world_the_end");
    }

    @Test
    void anotherMinecraftDimensionIsNamedByItsPath() {
        assertThat(DimensionNames.worldName(DimensionKey.of("minecraft:probeworld"), "world"))
                .isEqualTo("probeworld");
    }

    @Test
    void aDimensionOfAnotherNamespaceCarriesItsNamespaceInTheName() {
        assertThat(DimensionNames.worldName(DimensionKey.of("mypack:mining"), "world"))
                .isEqualTo("mypack_mining");
    }

    @Test
    void aNestedPathIsNotAWorld() {
        assertThat(DimensionNames.parse("mypack:caves/deep")).isEmpty();
    }

    @Test
    void theEnvironmentFollowsTheVanillaKeys() {
        assertThat(DimensionNames.environment("the_nether")).isEqualTo(WorldEnvironment.NETHER);
        assertThat(DimensionNames.environment("the_end")).isEqualTo(WorldEnvironment.THE_END);
        assertThat(DimensionNames.environment("overworld")).isEqualTo(WorldEnvironment.NORMAL);
        assertThat(DimensionNames.environment("mining")).isEqualTo(WorldEnvironment.NORMAL);
    }
}
