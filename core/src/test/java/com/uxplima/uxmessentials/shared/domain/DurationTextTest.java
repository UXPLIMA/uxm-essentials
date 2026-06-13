package com.uxplima.uxmessentials.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class DurationTextTest {

    @Test
    void zeroReturnsSuffix() {
        assertThat(DurationText.humanize(Duration.ZERO)).isEqualTo("0s");
    }

    @Test
    void secondsOnlyNoDays() {
        assertThat(DurationText.humanize(Duration.ofSeconds(45))).isEqualTo("45s");
    }

    @Test
    void minutesAndSeconds() {
        assertThat(DurationText.humanize(Duration.ofMinutes(1).plusSeconds(30))).isEqualTo("1m 30s");
    }

    @Test
    void hoursAndMinutes() {
        assertThat(DurationText.humanize(Duration.ofHours(1).plusMinutes(30))).isEqualTo("1h 30m");
    }

    @Test
    void daysHoursMinutesSeconds() {
        Duration d = Duration.ofDays(2).plusHours(3).plusMinutes(4).plusSeconds(5);
        assertThat(DurationText.humanize(d)).isEqualTo("2d 3h 4m 5s");
    }

    @Test
    void exactHoursDropsZeroMinutesAndSeconds() {
        assertThat(DurationText.humanize(Duration.ofHours(2))).isEqualTo("2h");
    }

    @Test
    void negativeDurationTreatedAsZero() {
        assertThat(DurationText.humanize(Duration.ofSeconds(-100))).isEqualTo("0s");
    }
}
