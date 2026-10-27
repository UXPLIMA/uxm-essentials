package com.uxplima.uxmessentials.kits.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

class KitScheduleTest {

    private static KitSchedule daily(String start, String end) {
        return new KitSchedule(
                Set.of(),
                Optional.of(LocalTime.parse(start)),
                Optional.of(LocalTime.parse(end)),
                Optional.empty(),
                Optional.empty());
    }

    private static LocalDateTime at(String time) {
        return LocalDateTime.parse("2030-01-01T" + time);
    }

    @Test
    void aKitWithoutAScheduleIsAlwaysAvailable() {
        assertThat(KitSchedule.always().isAvailableAt(at("03:00"))).isTrue();
    }

    @Test
    void aDailyWindowIncludesItsStartAndExcludesItsEnd() {
        KitSchedule evening = daily("18:00", "20:00");

        assertThat(evening.isAvailableAt(at("18:00"))).isTrue();
        assertThat(evening.isAvailableAt(at("19:59"))).isTrue();
        assertThat(evening.isAvailableAt(at("20:00"))).isFalse();
        assertThat(evening.isAvailableAt(at("17:59"))).isFalse();
    }

    @Test
    void aWindowThatEndsBeforeItStartsRunsPastMidnight() {
        KitSchedule night = daily("22:00", "06:00");

        assertThat(night.isAvailableAt(at("23:30"))).isTrue();
        assertThat(night.isAvailableAt(at("05:59"))).isTrue();
        assertThat(night.isAvailableAt(at("12:00"))).isFalse();
    }

    @Test
    void aDateRangeOpensAtItsStartAndClosesAtItsEnd() {
        KitSchedule event = new KitSchedule(
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(LocalDateTime.parse("2030-01-01T10:00")),
                Optional.of(LocalDateTime.parse("2030-01-02T10:00")));

        assertThat(event.isAvailableAt(at("09:59"))).isFalse();
        assertThat(event.isAvailableAt(at("10:00"))).isTrue();
        assertThat(event.isAvailableAt(LocalDateTime.parse("2030-01-02T10:00"))).isFalse();
    }
}
