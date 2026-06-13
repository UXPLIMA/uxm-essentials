package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.junit.jupiter.api.Test;

class TeleportSettingsArrivalEffectTest {

    @Test
    void disabledMasterToggleYieldsNoEffectEvenWhenAVerbIsConfigured() {
        TeleportSettings settings = settings(Map.of(
                "arrival-effects", false,
                "effects.home.enabled", true,
                "effects.home.particle", "END_ROD"));

        assertThat(settings.arrivalEffect(TeleportKind.HOME).enabled()).isFalse();
    }

    @Test
    void aVerbWithoutAnEnabledBlockYieldsTheNoneEffect() {
        TeleportSettings settings = settings(Map.of("arrival-effects", true));

        ArrivalEffect effect = settings.arrivalEffect(TeleportKind.WARP);

        assertThat(effect.enabled()).isFalse();
        assertThat(effect.hasSound()).isFalse();
        assertThat(effect.hasParticle()).isFalse();
    }

    @Test
    void anEnabledVerbReadsItsSoundAndParticleFields() {
        TeleportSettings settings = settings(Map.of(
                "arrival-effects",
                true,
                "effects.home.enabled",
                true,
                "effects.home.sound",
                "ENTITY_ENDERMAN_TELEPORT",
                "effects.home.sound-volume",
                0.5,
                "effects.home.sound-pitch",
                1.5,
                "effects.home.particle",
                "END_ROD",
                "effects.home.particle-count",
                30,
                "effects.home.particle-spread",
                0.3));

        ArrivalEffect effect = settings.arrivalEffect(TeleportKind.HOME);

        assertThat(effect.enabled()).isTrue();
        assertThat(effect.hasSound()).isTrue();
        assertThat(effect.sound()).isEqualTo("ENTITY_ENDERMAN_TELEPORT");
        assertThat(effect.soundVolume()).isEqualTo(0.5);
        assertThat(effect.soundPitch()).isEqualTo(1.5);
        assertThat(effect.hasParticle()).isTrue();
        assertThat(effect.particle()).isEqualTo("END_ROD");
        assertThat(effect.particleCount()).isEqualTo(30);
        assertThat(effect.particleSpread()).isEqualTo(0.3);
    }

    private static TeleportSettings settings(Map<String, Object> values) {
        return new TeleportSettings(new FixedConfig(values));
    }

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
            return values.get(path) instanceof Number n ? n.intValue() : fallback;
        }

        @Override
        public double getDouble(String path, double fallback) {
            return values.get(path) instanceof Number n ? n.doubleValue() : fallback;
        }

        @Override
        public List<String> getStringList(String path, List<String> fallback) {
            return fallback;
        }
    }
}
