package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/** A frame dropped for its size is reported to the operator at once, and a stream of them at most once a minute. */
class OversizeFrameReportTest {

    private final RecordingLogger log = new RecordingLogger();
    private long now = 1_763_208_000_000L;
    private final OversizeFrameReport report = new OversizeFrameReport(log, () -> now);

    @Test
    void theFirstDropWarnsAndLaterDropsWithinAMinuteDoNot() {
        report.dropped(40_000);
        now += 1_000L;
        report.dropped(40_000);
        now += OversizeFrameReport.INTERVAL_MILLIS - 2_000L;
        report.dropped(40_000);

        assertThat(log.warnings).containsExactly(List.of("40000", "32767", "1"));
    }

    @Test
    void aDropAMinuteAfterTheLastWarningWarnsAgainWithTheRunningCount() {
        report.dropped(40_000);
        now += 1_000L;
        report.dropped(50_000);
        now += OversizeFrameReport.INTERVAL_MILLIS;
        report.dropped(60_000);

        assertThat(log.warnings).containsExactly(List.of("40000", "32767", "1"), List.of("60000", "32767", "3"));
    }

    /** Keeps the arguments of every warning. */
    private static final class RecordingLogger implements Logger {

        final List<List<String>> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            List<String> values = new ArrayList<>();
            for (Object arg : args) {
                values.add(String.valueOf(arg));
            }
            warnings.add(values);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
