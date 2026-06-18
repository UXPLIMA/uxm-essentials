package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class FlatLayerPlanTest {

    @Test
    void defaultsAreClassicFlatWithFiveBlocksTotal() {
        FlatLayerPlan plan = FlatLayerPlan.defaults();
        assertThat(plan.layers()).hasSize(3);
        assertThat(plan.total()).isEqualTo(5);
        assertThat(plan.layers().get(0).block().namespacedValue()).isEqualTo("minecraft:bedrock");
        assertThat(plan.layers().get(0).height()).isEqualTo(1);
        assertThat(plan.layers().get(1).block().namespacedValue()).isEqualTo("minecraft:dirt");
        assertThat(plan.layers().get(1).height()).isEqualTo(3);
        assertThat(plan.layers().get(2).block().namespacedValue()).isEqualTo("minecraft:grass_block");
        assertThat(plan.layers().get(2).height()).isEqualTo(1);
    }

    @Test
    void parsesWellFormedConfigEntries() {
        FlatLayerPlan plan = FlatLayerPlan.parse(List.of("minecraft:bedrock 1", "dirt 3", "grass_block 1"));
        assertThat(plan.layers()).hasSize(3);
        assertThat(plan.total()).isEqualTo(5);
        assertThat(plan.layers().get(0).block().namespacedValue()).isEqualTo("minecraft:bedrock");
        assertThat(plan.layers().get(1).block().namespacedValue()).isEqualTo("minecraft:dirt");
        assertThat(plan.layers().get(2).block().namespacedValue()).isEqualTo("minecraft:grass_block");
    }

    @Test
    void skipsMalformedEntriesKeepingTheValidOnes() {
        FlatLayerPlan plan = FlatLayerPlan.parse(List.of("garbage", "stone 2"));
        assertThat(plan.layers()).hasSize(1);
        assertThat(plan.total()).isEqualTo(2);
        assertThat(plan.layers().get(0).block().namespacedValue()).isEqualTo("minecraft:stone");
        assertThat(plan.layers().get(0).height()).isEqualTo(2);
    }

    @Test
    void emptyInputFallsBackToDefaults() {
        assertThat(FlatLayerPlan.parse(List.of()).total()).isEqualTo(5);
    }

    @Test
    void allMalformedFallsBackToDefaults() {
        assertThat(FlatLayerPlan.parse(List.of("garbage", "stone -1", "dirt zero"))
                        .total())
                .isEqualTo(5);
    }

    @Test
    void layersAreImmutable() {
        FlatLayerPlan plan = FlatLayerPlan.defaults();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> plan.layers().add(new FlatLayer(BlockId.of("stone"), 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
