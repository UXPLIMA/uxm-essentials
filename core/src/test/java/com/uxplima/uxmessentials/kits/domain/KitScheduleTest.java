package com.uxplima.uxmessentials.kits.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

class KitScheduleTest {

    @Test
    void alwaysImposesNoConstraint() {
        assertThat(KitSchedule.always().isAlways()).isTrue();
        assertThat(KitSchedule.always().isAvailableAt(LocalDateTime.of(2026, 6, 10, 3, 0)))
                .isTrue();
    }

    @Test
    void aWeekdayRestrictionAdmitsOnlyListedDays() {
        KitSchedule weekend = days(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));

        assertThat(weekend.isAvailableAt(LocalDateTime.of(2026, 6, 13, 12, 0))).isTrue(); // Saturday
        assertThat(weekend.isAvailableAt(LocalDateTime.of(2026, 6, 10, 12, 0))).isFalse(); // Wednesday
    }

    @Test
    void aDailyWindowAdmitsInsideAndRefusesOutside() {
        KitSchedule evening = daily(LocalTime.of(20, 0), LocalTime.of(23, 0));

        assertThat(evening.isAvailableAt(LocalDateTime.of(2026, 6, 10, 21, 0))).isTrue();
        assertThat(evening.isAvailableAt(LocalDateTime.of(2026, 6, 10, 23, 0))).isFalse(); // end is exclusive
        assertThat(evening.isAvailableAt(LocalDateTime.of(2026, 6, 10, 12, 0))).isFalse();
    }

    @Test
    void anOvernightWindowWrapsPastMidnight() {
        KitSchedule overnight = daily(LocalTime.of(22, 0), LocalTime.of(6, 0));

        assertThat(overnight.isAvailableAt(LocalDateTime.of(2026, 6, 10, 23, 30)))
                .isTrue();
        assertThat(overnight.isAvailableAt(LocalDateTime.of(2026, 6, 10, 3, 0))).isTrue();
        assertThat(overnight.isAvailableAt(LocalDateTime.of(2026, 6, 10, 12, 0)))
                .isFalse();
    }

    @Test
    void anAbsoluteWindowBoundsTheCalendarRange() {
        KitSchedule window = new KitSchedule(
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(LocalDateTime.of(2026, 6, 1, 0, 0)),
                Optional.of(LocalDateTime.of(2026, 7, 1, 0, 0)));

        assertThat(window.isAvailableAt(LocalDateTime.of(2026, 6, 15, 12, 0))).isTrue();
        assertThat(window.isAvailableAt(LocalDateTime.of(2026, 5, 31, 23, 59))).isFalse();
        assertThat(window.isAvailableAt(LocalDateTime.of(2026, 7, 1, 0, 0))).isFalse(); // until is exclusive
    }

    private static KitSchedule days(Set<DayOfWeek> days) {
        return new KitSchedule(days, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static KitSchedule daily(LocalTime start, LocalTime end) {
        return new KitSchedule(Set.of(), Optional.of(start), Optional.of(end), Optional.empty(), Optional.empty());
    }
}
