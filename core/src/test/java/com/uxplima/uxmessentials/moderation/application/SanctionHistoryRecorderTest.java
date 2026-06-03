package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctionHistory;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The {@link SanctionHistoryRecorder} maps each ban/mute use case's success into one append: the right
 * {@link SanctionAction}, the acting staff as the issuer, and the expiry/IP that distinguish a timed or
 * IP-scoped sanction from a permanent UUID one. Every call appends exactly one row stamped at the recorder's
 * clock.
 */
class SanctionHistoryRecorderTest {

    private static final Instant NOW = Instant.parse("2026-06-02T00:00:00Z");
    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "staff");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "griefer");
    private static final String IP = "203.0.113.7";

    private FakeSanctionHistory store;
    private SanctionHistoryRecorder recorder;

    @BeforeEach
    void setUp() {
        store = new FakeSanctionHistory();
        recorder = new SanctionHistoryRecorder(store, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void permanentBanRecordsBanWithNoExpiryAndNoIp() {
        recorder.ban(ACTOR, TARGET, Optional.of("griefing"), Optional.empty());

        SanctionHistoryEntry row = single();
        assertThat(row.action()).isEqualTo(SanctionAction.BAN);
        assertThat(row.target()).isEqualTo(TARGET.uuid());
        assertThat(row.actor().uuid()).contains(ACTOR.uuid());
        assertThat(row.reason()).contains("griefing");
        assertThat(row.expiry()).isEmpty();
        assertThat(row.ip()).isEmpty();
        assertThat(row.at()).isEqualTo(NOW);
    }

    @Test
    void tempBanRecordsBanWithExpiry() {
        Instant until = NOW.plus(Duration.ofHours(1));
        recorder.ban(ACTOR, TARGET, Optional.empty(), Optional.of(until));

        SanctionHistoryEntry row = single();
        assertThat(row.action()).isEqualTo(SanctionAction.BAN);
        assertThat(row.expiry()).contains(until);
        assertThat(row.ip()).isEmpty();
    }

    @Test
    void banIpRecordsBanWithIpAndResolvedTarget() {
        recorder.banIp(ACTOR, Optional.of(TARGET.uuid()), IP, Optional.of("evasion"), Optional.empty());

        SanctionHistoryEntry row = single();
        assertThat(row.action()).isEqualTo(SanctionAction.BAN);
        assertThat(row.ip()).contains(IP);
        assertThat(row.target()).isEqualTo(TARGET.uuid());
    }

    @Test
    void banIpWithNoResolvedPlayerRecordsTheNilTarget() {
        recorder.banIp(ACTOR, Optional.empty(), IP, Optional.empty(), Optional.of(NOW.plus(Duration.ofDays(1))));

        SanctionHistoryEntry row = single();
        assertThat(row.target()).isEqualTo(new UUID(0L, 0L));
        assertThat(row.ip()).contains(IP);
        assertThat(row.expiry()).isPresent();
    }

    @Test
    void unbanRecordsUnbanWithNoExpiry() {
        recorder.unban(ACTOR, TARGET);

        SanctionHistoryEntry row = single();
        assertThat(row.action()).isEqualTo(SanctionAction.UNBAN);
        assertThat(row.target()).isEqualTo(TARGET.uuid());
        assertThat(row.ip()).isEmpty();
    }

    @Test
    void unbanIpRecordsUnbanWithIp() {
        recorder.unbanIp(ACTOR, IP);

        SanctionHistoryEntry row = single();
        assertThat(row.action()).isEqualTo(SanctionAction.UNBAN);
        assertThat(row.ip()).contains(IP);
        assertThat(row.target()).isEqualTo(new UUID(0L, 0L));
    }

    @Test
    void muteRecordsMuteWithExpiryWhenTimed() {
        Instant until = NOW.plus(Duration.ofMinutes(30));
        recorder.mute(ACTOR, TARGET, Optional.of("spam"), Optional.of(until));

        SanctionHistoryEntry row = single();
        assertThat(row.action()).isEqualTo(SanctionAction.MUTE);
        assertThat(row.expiry()).contains(until);
    }

    @Test
    void unmuteRecordsUnmute() {
        recorder.unmute(ACTOR, TARGET);

        SanctionHistoryEntry row = single();
        assertThat(row.action()).isEqualTo(SanctionAction.UNMUTE);
        assertThat(row.target()).isEqualTo(TARGET.uuid());
    }

    private SanctionHistoryEntry single() {
        assertThat(store.appended).hasSize(1);
        return store.appended.get(0);
    }
}
