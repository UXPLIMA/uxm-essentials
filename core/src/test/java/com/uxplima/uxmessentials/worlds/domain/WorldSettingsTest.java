package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class WorldSettingsTest {

    @Test
    void defaultsAreEmptyAndReturnPropertyDefaults() {
        WorldSettings settings = WorldSettings.defaults();
        assertThat(settings.raw()).isEmpty();
        assertThat(settings.get(WorldProperties.PVP)).isTrue();
        assertThat(settings.get(WorldProperties.DIFFICULTY)).isEqualTo(WorldDifficulty.NORMAL);
    }

    @Test
    void withStoresEncodedValueAndGetDecodesIt() {
        WorldSettings settings = WorldSettings.defaults()
                .with(WorldProperties.PVP, false)
                .with(WorldProperties.DIFFICULTY, WorldDifficulty.HARD);
        assertThat(settings.raw()).containsEntry("pvp", "false").containsEntry("difficulty", "HARD");
        assertThat(settings.get(WorldProperties.PVP)).isFalse();
        assertThat(settings.get(WorldProperties.DIFFICULTY)).isEqualTo(WorldDifficulty.HARD);
    }

    @Test
    void corruptRawFallsBackToDefault() {
        WorldSettings settings = WorldSettings.fromRaw(Map.of("pvp", "garbage"));
        assertThat(settings.get(WorldProperties.PVP)).isTrue(); // default, since "garbage" won't decode
    }

    @Test
    void gamerulesAndSpawnHelpers() {
        WorldSettings settings = WorldSettings.defaults()
                .withRaw("gamerule.keepInventory", "true")
                .withRaw("gamerule.mobGriefing", "false")
                .withRaw("spawn", "10;64;20;0.0;0.0");
        assertThat(settings.gamerules()).containsEntry("keepInventory", "true").containsEntry("mobGriefing", "false");
        assertThat(settings.spawn()).contains("10;64;20;0.0;0.0");
        assertThat(settings.withoutRaw("spawn").spawn()).isEmpty();
    }

    @Test
    void immutabilityCopiesOnWrite() {
        WorldSettings base = WorldSettings.defaults();
        base.with(WorldProperties.PVP, false);
        assertThat(base.raw()).isEmpty(); // original unchanged
    }
}
