package com.uxplima.uxmessentials.teleport.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.teleport.domain.SearchBudget;
import org.junit.jupiter.api.Test;

/**
 * The {@code /settpr} runtime swap on {@link RtpWorldSettings#withRadii(double, double)} and the config parse.
 * {@code withRadii} resets the search band while leaving the queue sizing and the per-search budget untouched,
 * keeping the record's {@code maxRadius >= minRadius} invariant; {@link RtpWorldSettings#from(ConfigStore)}
 * reads the three budget ceilings with their documented defaults.
 */
class RtpWorldSettingsTest {

    private static final RtpWorldSettings BASE = new RtpWorldSettings(100, 5000, 10, 5, new SearchBudget(40, 30, 2500));

    @Test
    void withRadiiSwapsTheBandAndKeepsTheQueueTuning() {
        RtpWorldSettings updated = BASE.withRadii(250, 8000);

        assertThat(updated.minRadius()).isEqualTo(250);
        assertThat(updated.maxRadius()).isEqualTo(8000);
        assertThat(updated.targetSize()).isEqualTo(BASE.targetSize());
        assertThat(updated.lowWaterMark()).isEqualTo(BASE.lowWaterMark());
        assertThat(updated.searchBudget()).isEqualTo(BASE.searchBudget());
    }

    @Test
    void withRadiiRejectsAMaxBelowTheMin() {
        assertThatThrownBy(() -> BASE.withRadii(2000, 1000)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromReadsTheSearchBudgetDefaultsWhenAbsent() {
        RtpWorldSettings defaults = RtpWorldSettings.from(new FixedConfig(Map.of()));

        assertThat(defaults.searchBudget()).isEqualTo(new SearchBudget(40, 30, 2500));
    }

    @Test
    void fromReadsExplicitSearchBudgetOverrides() {
        RtpWorldSettings settings = RtpWorldSettings.from(new FixedConfig(Map.of(
                "rtp.max-attempts", 12,
                "rtp.max-chunk-loads", 8,
                "rtp.max-wall-clock-ms", 900)));

        assertThat(settings.searchBudget()).isEqualTo(new SearchBudget(12, 8, 900));
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
    }
}
