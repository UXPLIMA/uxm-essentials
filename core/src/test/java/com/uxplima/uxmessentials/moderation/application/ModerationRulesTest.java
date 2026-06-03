package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctions;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.moderation.fakes.RecordingModerationAudit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The sanction use-case rules through the real use cases against in-memory fakes: a timed mute is stored and
 * audited, an exempt target is refused, a tempban without a duration is rejected, an online tempban kicks, a
 * warn appends to history, and an offline target's row is materialized before the FK-bearing write. Every
 * action audits exactly once, ok=true on success and ok=false on a refusal.
 */
class ModerationRulesTest {

    private static final Instant T0 = Instant.parse("2026-05-31T00:00:00Z");
    private static final PlayerRef ADMIN = new PlayerRef(UUID.randomUUID(), "admin");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "griefer");
    private static final PlayerRef EXEMPT = new PlayerRef(UUID.randomUUID(), "vip");

    private FakeModerationRepository repository;
    private RecordingModerationAudit audit;
    private ModerationFakes.RecordingEvents events;
    private Clock clock;
    private ModerationGuard guard;

    @BeforeEach
    void setUp() {
        repository = new FakeModerationRepository();
        audit = new RecordingModerationAudit();
        events = new ModerationFakes.RecordingEvents();
        clock = Clock.fixed(T0, ZoneOffset.UTC);
        guard = new ModerationGuard(ModerationFakes.exempt(EXEMPT.uuid()));
    }

    @Test
    void timedMuteIsStoredAuditedAndMaterializesAnOfflineRow() {
        Mute mute = new Mute(repository, guard, ModerationFakes.notifier(), audit, events, clock);

        var result = mute.mute(ADMIN, TARGET, "1h30m", Optional.of("spam"));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.loadMute(TARGET)).isInstanceOf(MuteState.Timed.class);
        assertThat(repository.loadMute(TARGET).isActiveAt(T0)).isTrue();
        assertThat(repository.loadMute(TARGET).isActiveAt(T0.plus(Duration.ofHours(2))))
                .isFalse();
        assertThat(repository.ensured).contains(TARGET.uuid());
        assertThat(audit.lines).singleElement().satisfies(line -> {
            assertThat(line.event()).isEqualTo("player_mute");
            assertThat(line.ok()).isTrue();
        });
    }

    @Test
    void exemptTargetCannotBeMutedAndTheRefusalIsAudited() {
        Mute mute = new Mute(repository, guard, ModerationFakes.notifier(), audit, events, clock);

        var result = mute.mute(ADMIN, EXEMPT, "", Optional.empty());

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(ModerationError.TARGET_EXEMPT);
        assertThat(repository.loadMute(EXEMPT)).isInstanceOf(MuteState.None.class);
        assertThat(audit.lines).singleElement().satisfies(line -> assertThat(line.ok())
                .isFalse());
    }

    @Test
    void malformedMuteDurationIsRejected() {
        Mute mute = new Mute(repository, guard, ModerationFakes.notifier(), audit, events, clock);

        var result = mute.mute(ADMIN, TARGET, "10x", Optional.empty());

        assertThat(result.errorOrThrow()).isEqualTo(ModerationError.BAD_DURATION);
        assertThat(repository.loadMute(TARGET)).isInstanceOf(MuteState.None.class);
    }

    @Test
    void unmuteOfAnUnmutedPlayerIsRefused() {
        Unmute unmute = new Unmute(repository, ModerationFakes.notifier(), audit, events, clock);

        var result = unmute.unmute(ADMIN, TARGET);

        assertThat(result.errorOrThrow()).isEqualTo(ModerationError.NOT_MUTED);
        assertThat(audit.lines).singleElement().satisfies(line -> assertThat(line.ok())
                .isFalse());
    }

    @Test
    void tempbanRequiresADurationAndKicksAnOnlineTarget() {
        FakeSanctions sanctions = new FakeSanctions(TARGET);
        TempBan tempBan = new TempBan(repository, sanctions, guard, ModerationFakes.notifier(), audit, events, clock);

        assertThat(tempBan.tempban(ADMIN, TARGET, "", Optional.empty()).errorOrThrow())
                .isEqualTo(ModerationError.BAD_DURATION);

        var ok = tempBan.tempban(ADMIN, TARGET, "2h", Optional.of("cheating"));
        assertThat(ok.isOk()).isTrue();
        assertThat(repository.loadTempban(TARGET)).isInstanceOf(TempbanState.Active.class);
        assertThat(sanctions.kicked).containsExactly(TARGET);
    }

    @Test
    void jailStoresTheSentenceAndTeleportsAnOnlineTarget() {
        FakeSanctions sanctions = new FakeSanctions(TARGET);
        Jail jail = new Jail(
                repository,
                ModerationFakes.jails(Set.of("cells"), Set.of()),
                sanctions,
                guard,
                ModerationFakes.notifier(),
                audit,
                events,
                clock);

        var result = jail.jail(ADMIN, TARGET, "cells", "10m", Optional.empty());

        assertThat(result.isOk()).isTrue();
        assertThat(repository.loadJail(TARGET)).isInstanceOf(JailState.Active.class);
        assertThat(((JailState.Active) repository.loadJail(TARGET)).isOnlineTimed())
                .isTrue();
        assertThat(sanctions.jailedInto).containsExactly("cells");
    }

    @Test
    void jailRejectsAnUnknownJailName() {
        Jail jail = new Jail(
                repository,
                ModerationFakes.jails(Set.of("cells"), Set.of()),
                new FakeSanctions(),
                guard,
                ModerationFakes.notifier(),
                audit,
                events,
                clock);

        var result = jail.jail(ADMIN, TARGET, "nowhere", "", Optional.empty());

        assertThat(result.errorOrThrow()).isEqualTo(ModerationError.UNKNOWN_JAIL);
        assertThat(repository.loadJail(TARGET)).isInstanceOf(JailState.None.class);
    }

    @Test
    void warnAppendsToHistoryAndReportsTheRunningTotal() {
        IssueWarn warn = new IssueWarn(repository, guard, ModerationFakes.notifier(), audit, events, clock);

        warn.warn(ADMIN, TARGET, Optional.of("first"));
        var second = warn.warn(ADMIN, TARGET, Optional.of("second"));

        assertThat(second.orElseThrow().totalWarnings()).isEqualTo(2);
        assertThat(repository.warns(TARGET, T0)).hasSize(2);
        // newest-first
        assertThat(repository.warns(TARGET, T0).get(0).reason()).contains("second");
    }

    @Test
    void tempWarnAppendsATimedWarningThatLapsesOutOfTheReadAtExpiry() {
        TempWarn tempWarn = new TempWarn(repository, guard, ModerationFakes.notifier(), audit, events, clock);

        var result = tempWarn.warn(ADMIN, TARGET, "1h", Optional.of("cooldown"));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.warns(TARGET, T0.plus(Duration.ofMinutes(30)))).hasSize(1);
        assertThat(repository.warns(TARGET, T0.plus(Duration.ofHours(2)))).isEmpty();
        assertThat(repository.ensured).containsExactly(TARGET.uuid());
    }

    @Test
    void tempWarnWithoutADurationIsRejected() {
        TempWarn tempWarn = new TempWarn(repository, guard, ModerationFakes.notifier(), audit, events, clock);

        var result = tempWarn.warn(ADMIN, TARGET, "", Optional.empty());

        assertThat(result.errorOrThrow()).isEqualTo(ModerationError.BAD_DURATION);
        assertThat(repository.warns(TARGET, T0)).isEmpty();
    }
}
