package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/** A refused frame is reported at once, a stream of them at most once a minute, and every one is counted. */
class RefusedFrameReportTest {

    private final RecordingLogger log = new RecordingLogger();
    private long now = 1_763_208_000_000L;
    private final RefusedFrameReport report = new RefusedFrameReport(log, () -> now);

    @Test
    void theFirstRefusalWarnsAndLaterOnesWithinAMinuteOnlyCount() {
        report.refused(FrameSeal.Check.SIGNATURE);
        now += 1_000L;
        report.refused(FrameSeal.Check.CLOCK);
        now += RefusedFrameReport.INTERVAL_MILLIS - 2_000L;
        report.refused(FrameSeal.Check.SIGNATURE);

        assertThat(log.warnings).containsExactly(List.of("1"));
        assertThat(report.count()).isEqualTo(3);
        assertThat(report.last()).hasValue(now);
    }

    @Test
    void aRefusalAMinuteAfterTheLastWarningWarnsAgainWithTheRunningCount() {
        report.refused(FrameSeal.Check.SIGNATURE);
        now += 1_000L;
        report.refused(FrameSeal.Check.SIGNATURE);
        now += RefusedFrameReport.INTERVAL_MILLIS;
        report.refused(FrameSeal.Check.CLOCK);

        assertThat(log.warnings).containsExactly(List.of("1"), List.of("60", "3"));
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
