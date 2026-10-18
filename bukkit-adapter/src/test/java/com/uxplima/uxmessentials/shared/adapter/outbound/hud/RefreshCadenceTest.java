package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

class RefreshCadenceTest {

    @Test
    void theTopLevelValueDrivesTheCadenceWhenTheBlockKeepsTheShippedDefault() throws Exception {
        // Issue 3: 0.10.0 shipped both keys at 20, and the operator changed the one the comment calls global.
        RecordingLogger log = new RecordingLogger();

        Duration cadence = resolve("refresh-ticks = 100\nscoreboard { refresh-ticks = 20 }", log);

        assertThat(cadence).isEqualTo(Duration.ofSeconds(5L));
        assertThat(log.warnings).isEmpty();
    }

    @Test
    void aBlockValueTheOperatorChangedStillWinsOverTheShippedTopLevelDefault() throws Exception {
        // Up to 0.10.0 the block value was the one read in this shape, so a server tuned it there.
        Duration cadence = resolve("refresh-ticks = 20\nscoreboard { refresh-ticks = 40 }", new RecordingLogger());

        assertThat(cadence).isEqualTo(Duration.ofSeconds(2L));
    }

    @Test
    void theTopLevelValueWinsAndIsReportedWhenBothWereChanged() throws Exception {
        RecordingLogger log = new RecordingLogger();

        Duration cadence = resolve("refresh-ticks = 10\nscoreboard { refresh-ticks = 40 }", log);

        assertThat(cadence).isEqualTo(Duration.ofMillis(500L));
        assertThat(log.warnings)
                .containsExactly("event=refresh_ticks_conflict used=refresh-ticks ignored=scoreboard.refresh-ticks");
    }

    @Test
    void eitherKeyAloneIsReadAndNeitherFallsBackToOneSecond() throws Exception {
        assertThat(resolve("refresh-ticks = 40\nscoreboard {}", new RecordingLogger()))
                .isEqualTo(Duration.ofSeconds(2L));
        assertThat(resolve("scoreboard { refresh-ticks = 60 }", new RecordingLogger()))
                .isEqualTo(Duration.ofSeconds(3L));
        assertThat(resolve("scoreboard {}", new RecordingLogger())).isEqualTo(Duration.ofSeconds(1L));
    }

    @Test
    void aNonPositiveValueFallsBackToOneSecond() throws Exception {
        assertThat(resolve("refresh-ticks = 0\nscoreboard { refresh-ticks = 20 }", new RecordingLogger()))
                .isEqualTo(Duration.ofSeconds(1L));
        assertThat(RefreshCadence.of(load("refresh-ticks = -5").node("refresh-ticks")))
                .isEqualTo(Duration.ofSeconds(1L));
    }

    private static Duration resolve(String hocon, Logger log) throws ConfigurateException {
        ConfigurationNode root = load(hocon);
        return RefreshCadence.resolve(root, root.node("scoreboard"), "scoreboard", log);
    }

    private static ConfigurationNode load(String hocon) throws ConfigurateException {
        return HoconConfigurationLoader.builder().buildAndLoadString(hocon);
    }

    private static final class RecordingLogger implements Logger {
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            String out = message;
            for (Object arg : args) {
                out = out.replaceFirst("\\{}", String.valueOf(arg));
            }
            warnings.add(out);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
