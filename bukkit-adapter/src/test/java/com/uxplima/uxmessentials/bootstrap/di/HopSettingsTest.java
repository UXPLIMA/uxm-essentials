package com.uxplima.uxmessentials.bootstrap.di;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/** The cross-server hop timings, read from config.conf or from where a file of an earlier release keeps them. */
class HopSettingsTest {

    private final List<String> warnings = new ArrayList<>();
    private final Logger log = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };

    private HopWiring.Settings read(Map<String, Integer> values) {
        return HopWiring.Settings.read(new IntConfig(values), log);
    }

    @Test
    void timingsAnOperatorSetUnderPlayerWarpsAreKeptWhileTheNewBlockHoldsTheShippedValues() {
        HopWiring.Settings settings = read(Map.of(
                "cross-server.arrival-delay-ticks", 20,
                "cross-server.request-ttl-seconds", 30,
                "modules.playerwarps.cross-server.arrival-delay-ticks", 40,
                "modules.playerwarps.cross-server.request-ttl-seconds", 90));

        assertThat(settings.arrivalDelay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(settings.requestTtl()).isEqualTo(Duration.ofSeconds(90));
        assertThat(warnings).isEmpty();
    }

    @Test
    void timingsWrittenInTheNewBlockWinAndTheOldLineIsNamed() {
        HopWiring.Settings settings = read(Map.of(
                "cross-server.request-ttl-seconds", 45, "modules.playerwarps.cross-server.request-ttl-seconds", 90));

        assertThat(settings.requestTtl()).isEqualTo(Duration.ofSeconds(45));
        assertThat(warnings).hasSize(1);
    }

    @Test
    void aFileWithNeitherReadsTheShippedTimings() {
        HopWiring.Settings settings = read(Map.of());

        assertThat(settings.arrivalDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(settings.requestTtl()).isEqualTo(Duration.ofSeconds(30));
    }

    /** A config tree holding only whole numbers. */
    private record IntConfig(Map<String, Integer> values) implements ConfigStore {

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.getOrDefault(path, fallback);
        }
    }
}
