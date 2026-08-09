package com.uxplima.uxmessentials.playerstate.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmPlaytime;
import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.playerstate.domain.PlaytimeSummary;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published playtime query: the read waits on the database, the windows are anchored on the server's day, and
 * a player nobody has sampled reads as zero rather than as absent.
 */
class PlaytimeQueriesTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final Clock NOON = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);

    private FakePlaytimeRepository repository;
    private QueryDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = new FakePlaytimeRepository();
        scheduler = new QueryDoubles.InlineScheduler();
    }

    @Test
    void theReadRunsOffTheCallingThread() {
        queries().of(PLAYER).join();

        assertThat(scheduler.asyncCalls()).isEqualTo(1);
    }

    @Test
    void theWindowIsAnchoredOnTheServersDayRatherThanOnTheCallersClock() {
        queries().of(PLAYER).join();

        assertThat(repository.askedFor).isEqualTo(LocalDate.of(2026, 8, 9));
    }

    @Test
    void everyWindowComesBackAsTheLedgerSummedIt() {
        repository.summary = PlaytimeSummary.ofSeconds(60L, 30L, 600L, 120L, 3_600L, 300L, 7_200L, 900L);

        UxmPlaytime playtime = queries().of(PLAYER).join();

        assertThat(playtime.todayActive()).isEqualTo(Duration.ofMinutes(1));
        assertThat(playtime.todayAfk()).isEqualTo(Duration.ofSeconds(30));
        assertThat(playtime.weekActive()).isEqualTo(Duration.ofMinutes(10));
        assertThat(playtime.monthActive()).isEqualTo(Duration.ofHours(1));
        assertThat(playtime.totalActive()).isEqualTo(Duration.ofHours(2));
        assertThat(playtime.totalAfk()).isEqualTo(Duration.ofMinutes(15));
        assertThat(playtime.totalConnected()).isEqualTo(Duration.ofMinutes(135));
    }

    @Test
    void aPlayerNobodyHasSampledReadsAsZeroRatherThanAsAbsent() {
        assertThat(queries().of(PLAYER).join()).isEqualTo(UxmPlaytime.empty());
    }

    private PlaytimeQueries queries() {
        return new PlaytimeQueries(repository, scheduler, NOON);
    }

    /** Answers one summary and records the day it was asked about, with both writes left as traps. */
    private static final class FakePlaytimeRepository implements PlaytimeRepository {

        private PlaytimeSummary summary = PlaytimeSummary.empty();
        private @Nullable LocalDate askedFor;

        @Override
        public void addSeconds(UUID uuid, LocalDate day, long activeDelta, long afkDelta) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public PlaytimeSummary summaryOf(UUID uuid, LocalDate today) {
            askedFor = today;
            return summary;
        }

        @Override
        public void reset(UUID uuid) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public void resetAll() {
            throw new AssertionError("a query must never write");
        }
    }
}
