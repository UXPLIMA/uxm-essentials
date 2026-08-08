package com.uxplima.uxmessentials.villagers.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins the villagers module's typed config view: the module ships disabled, and once turned on carries the common trade tools on by default
 * (infinite trading, the restock timer, the trade manager, click-to-trade, protection, and follow) while the niche or
 * disruptive features stay off (instant restock, disable-trades, bucket, and leash), the restock interval defaults to
 * ten minutes and is clamped to at least one second, and explicit overrides are read back.
 */
class VillagersConfigTest {

    @Test
    void moduleEnabledWithCommonToolsOnAndNicheFeaturesOffByDefault() {
        VillagersConfig config = VillagersConfig.from(new FixedConfig(Map.of()));

        assertThat(config.enabled()).isTrue();
        assertThat(config.infiniteTrading().enabled()).isTrue();
        assertThat(config.restock().enabled()).isTrue();
        assertThat(config.instantRestock().enabled()).isFalse();
        assertThat(config.disableTrades().enabled()).isFalse();
        assertThat(config.tradeManager().enabled()).isTrue();
        assertThat(config.clickToTrade().enabled()).isTrue();
        assertThat(config.protect().enabled()).isTrue();
        assertThat(config.bucket().enabled()).isFalse();
        assertThat(config.follow().enabled()).isTrue();
        assertThat(config.leash().enabled()).isFalse();
    }

    @Test
    void followDefaultsToNormalSpeedAndSixteenBlocks() {
        VillagersConfig config = VillagersConfig.from(new FixedConfig(Map.of()));

        assertThat(config.follow().speed()).isEqualTo(1.0);
        assertThat(config.follow().range()).isEqualTo(16);
        assertThat(config.follow().followRange().range()).isEqualTo(16.0);
    }

    @Test
    void followSpeedAndRangeAreClampedToSaneValues() {
        VillagersConfig config = VillagersConfig.from(
                new FixedConfig(Map.ofEntries(Map.entry("follow.speed", "0"), Map.entry("follow.range", 0))));

        assertThat(config.follow().speed()).isEqualTo(1.0); // a non-positive speed falls back to the default
        assertThat(config.follow().range()).isEqualTo(1); // clamped to at least one block
    }

    @Test
    void protectionShipsOnWithItsPerThreatGatesDefaultOn() {
        VillagersConfig config = VillagersConfig.from(new FixedConfig(Map.of()));

        assertThat(config.protect().enabled()).isTrue();
        assertThat(config.protect().all()).isFalse();
        assertThat(config.protect().fromZombies()).isTrue();
        assertThat(config.protect().fromLightning()).isTrue();
        assertThat(config.protect().fromDamage()).isTrue();
        assertThat(config.protect().noDespawn()).isTrue();
        // With the feature on and every gate on, a marked villager is shielded from despawn; an unmarked one is not
        // (all is off by default, so only villagers flagged with /villager protect fall in scope).
        assertThat(config.protect().policy().protectsDespawn(true)).isTrue();
        assertThat(config.protect().policy().protectsDespawn(false)).isFalse();
    }

    @Test
    void restockIntervalDefaultsToTenMinutes() {
        VillagersConfig config = VillagersConfig.from(new FixedConfig(Map.of()));

        assertThat(config.restock().intervalSeconds()).isEqualTo(600);
        assertThat(config.restock().interval()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void restockIntervalIsClampedToAtLeastOneSecond() {
        VillagersConfig config = VillagersConfig.from(new FixedConfig(Map.of("restock.interval-seconds", 0)));

        assertThat(config.restock().intervalSeconds()).isEqualTo(1);
        assertThat(config.restock().interval()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void explicitOverridesAreReadBack() {
        VillagersConfig config = VillagersConfig.from(new FixedConfig(Map.ofEntries(
                Map.entry("enabled", false),
                Map.entry("infinite-trading.enabled", true),
                Map.entry("restock.enabled", true),
                Map.entry("restock.interval-seconds", 120),
                Map.entry("instant-restock.enabled", true),
                Map.entry("disable-trades.enabled", true),
                Map.entry("trade-manager.enabled", true),
                Map.entry("click-to-trade.enabled", true),
                Map.entry("protect.enabled", true),
                Map.entry("protect.all", true),
                Map.entry("protect.from-zombies", false),
                Map.entry("bucket.enabled", true),
                Map.entry("follow.enabled", true),
                Map.entry("follow.speed", "1.5"),
                Map.entry("follow.range", 24),
                Map.entry("leash.enabled", true))));

        assertThat(config.enabled()).isFalse();
        assertThat(config.infiniteTrading().enabled()).isTrue();
        assertThat(config.restock().enabled()).isTrue();
        assertThat(config.restock().intervalSeconds()).isEqualTo(120);
        assertThat(config.instantRestock().enabled()).isTrue();
        assertThat(config.disableTrades().enabled()).isTrue();
        assertThat(config.tradeManager().enabled()).isTrue();
        assertThat(config.clickToTrade().enabled()).isTrue();
        assertThat(config.protect().enabled()).isTrue();
        assertThat(config.protect().all()).isTrue();
        assertThat(config.protect().fromZombies()).isFalse();
        assertThat(config.bucket().enabled()).isTrue();
        assertThat(config.follow().enabled()).isTrue();
        assertThat(config.follow().speed()).isEqualTo(1.5);
        assertThat(config.follow().range()).isEqualTo(24);
        assertThat(config.leash().enabled()).isTrue();
    }

    /** A map-backed {@link ConfigStore} addressing keys by their dotted path relative to the module root. */
    private record FixedConfig(Map<String, Object> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }
    }
}
