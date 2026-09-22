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
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctionHistory;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctions;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.moderation.fakes.RecordingModerationAudit;
import com.uxplima.uxmessentials.shared.application.port.FakeIpHistoryStore;
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
    private SanctionHistoryRecorder history;

    @BeforeEach
    void setUp() {
        repository = new FakeModerationRepository();
        audit = new RecordingModerationAudit();
        events = new ModerationFakes.RecordingEvents();
        clock = Clock.fixed(T0, ZoneOffset.UTC);
        guard = new ModerationGuard(ModerationFakes.exempt(EXEMPT.uuid()));
        history = new SanctionHistoryRecorder(new FakeSanctionHistory(), clock);
    }

    @Test
    void timedMuteIsStoredAuditedAndMaterializesAnOfflineRow() {
        Mute mute = mute();

        var result = mute.mute(ADMIN, TARGET, "1h30m", Optional.of("spam"), false);

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
    void mutingAnAlreadyMutedPlayerIsRefused() {
        // ALREADY_MUTED was declared, and the public API promised ALREADY_IN_STATE for it, and no use case
        // ever returned it: a second /mute overwrote the first in silence, and moderation.mute.already
        // shipped in twelve languages unsent.
        Mute mute = mute();
        mute.mute(ADMIN, TARGET, "1h", Optional.of("spam"), false);

        var result = mute.mute(ADMIN, TARGET, "2h", Optional.of("spam again"), false);

        assertThat(result.errorOrThrow()).isEqualTo(ModerationError.ALREADY_MUTED);
        assertThat(repository.loadMute(TARGET).isActiveAt(T0.plus(Duration.ofMinutes(90))))
                .as("the first sentence stands; the operator unmutes before they re-mute")
                .isFalse();
    }

    @Test
    void anExpiredMuteIsNotAnAlreadyMutedRefusal() {
        Mute mute = mute();
        mute.mute(ADMIN, TARGET, "1h", Optional.of("spam"), false);

        var result = muteAt(T0.plus(Duration.ofHours(2))).mute(ADMIN, TARGET, "1h", Optional.empty(), false);

        assertThat(result.isOk())
                .as("the refusal is about a sentence in force, not about one the player has served")
                .isTrue();
    }

    @Test
    void exemptTargetCannotBeMutedAndTheRefusalIsAudited() {
        Mute mute = mute();

        var result = mute.mute(ADMIN, EXEMPT, "", Optional.empty(), false);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(ModerationError.TARGET_EXEMPT);
        assertThat(repository.loadMute(EXEMPT)).isInstanceOf(MuteState.None.class);
        assertThat(audit.lines)
                .singleElement()
                .satisfies(line -> assertThat(line.ok()).isFalse());
    }

    @Test
    void malformedMuteDurationIsRejected() {
        Mute mute = mute();

        var result = mute.mute(ADMIN, TARGET, "10x", Optional.empty(), false);

        assertThat(result.errorOrThrow()).isEqualTo(ModerationError.BAD_DURATION);
        assertThat(repository.loadMute(TARGET)).isInstanceOf(MuteState.None.class);
    }

    @Test
    void unmuteOfAnUnmutedPlayerIsRefused() {
        Unmute unmute = new Unmute(repository, ModerationFakes.notifier(), audit, events, history, clock);

        var result = unmute.unmute(ADMIN, TARGET);

        assertThat(result.errorOrThrow()).isEqualTo(ModerationError.NOT_MUTED);
        assertThat(audit.lines)
                .singleElement()
                .satisfies(line -> assertThat(line.ok()).isFalse());
    }

    @Test
    void tempbanRequiresADurationAndKicksAnOnlineTarget() {
        FakeSanctions sanctions = new FakeSanctions(TARGET);
        TempBan tempBan = new TempBan(
                repository,
                sanctions,
                guard,
                ModerationFakes.notifier(),
                audit,
                events,
                history,
                new SanctionDurationLimit(ModerationFakes.exempt()),
                ModerationFakes.broadcast(),
                com.uxplima.uxmessentials.moderation.application.port.SanctionSync.NONE,
                clock);

        assertThat(tempBan.tempban(ADMIN, TARGET, "", Optional.empty(), false).errorOrThrow())
                .isEqualTo(ModerationError.BAD_DURATION);

        var ok = tempBan.tempban(ADMIN, TARGET, "2h", Optional.of("cheating"), false);
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
    void jailingAnAlreadyJailedPlayerIsRefused() {
        // The same unkept promise as the mute: moderation.jail.already was written, no code refused
        // anything, and a second /jail silently replaced the sentence the first one gave.
        Jail jail = new Jail(
                repository,
                ModerationFakes.jails(Set.of("cells", "block-a"), Set.of()),
                new FakeSanctions(),
                guard,
                ModerationFakes.notifier(),
                audit,
                events,
                clock);
        jail.jail(ADMIN, TARGET, "cells", "", Optional.empty());

        var result = jail.jail(ADMIN, TARGET, "block-a", "", Optional.empty());

        assertThat(result.errorOrThrow()).isEqualTo(ModerationError.ALREADY_JAILED);
        assertThat(((JailState.Active) repository.loadJail(TARGET)).jail())
                .as("the first sentence stands; the operator unjails before they jail again")
                .isEqualTo("cells");
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
        IssueWarn warn = issueWarn();

        warn.warn(ADMIN, TARGET, Optional.of("first"), false);
        var second = warn.warn(ADMIN, TARGET, Optional.of("second"), false);

        assertThat(second.orElseThrow().totalWarnings()).isEqualTo(2);
        assertThat(repository.warns(TARGET, T0)).hasSize(2);
        // newest-first
        assertThat(repository.warns(TARGET, T0).get(0).reason()).contains("second");
    }

    @Test
    void tempWarnAppendsATimedWarningThatLapsesOutOfTheReadAtExpiry() {
        TempWarn tempWarn = tempWarn();

        var result = tempWarn.warn(ADMIN, TARGET, "1h", Optional.of("cooldown"), false);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.warns(TARGET, T0.plus(Duration.ofMinutes(30)))).hasSize(1);
        assertThat(repository.warns(TARGET, T0.plus(Duration.ofHours(2)))).isEmpty();
        assertThat(repository.ensured).containsExactly(TARGET.uuid());
    }

    @Test
    void tempWarnWithoutADurationIsRejected() {
        TempWarn tempWarn = tempWarn();

        var result = tempWarn.warn(ADMIN, TARGET, "", Optional.empty(), false);

        assertThat(result.errorOrThrow()).isEqualTo(ModerationError.BAD_DURATION);
        assertThat(repository.warns(TARGET, T0)).isEmpty();
    }

    private Mute mute() {
        return muteAt(T0);
    }

    /** The mute use case reading the clock at {@code when}, so a test can let a sentence run out. */
    private Mute muteAt(java.time.Instant when) {
        return new Mute(
                repository,
                guard,
                ModerationFakes.notifier(),
                audit,
                events,
                history,
                new SanctionDurationLimit(ModerationFakes.exempt()),
                ModerationFakes.broadcast(),
                com.uxplima.uxmessentials.moderation.application.port.SanctionSync.NONE,
                java.time.Clock.fixed(when, java.time.ZoneOffset.UTC));
    }

    private IssueWarn issueWarn() {
        return new IssueWarn(
                repository,
                guard,
                ModerationFakes.notifier(),
                audit,
                events,
                history,
                ModerationFakes.broadcast(),
                noEscalation(),
                clock);
    }

    private TempWarn tempWarn() {
        return new TempWarn(
                repository,
                guard,
                ModerationFakes.notifier(),
                audit,
                events,
                history,
                ModerationFakes.broadcast(),
                noEscalation(),
                clock);
    }

    private WarnEscalator noEscalation() {
        Mute mute = mute();
        SanctionDurationLimit limit = new SanctionDurationLimit(ModerationFakes.exempt());
        TempBan tempBan = new TempBan(
                repository,
                new FakeSanctions(),
                guard,
                ModerationFakes.notifier(),
                audit,
                events,
                history,
                limit,
                ModerationFakes.broadcast(),
                com.uxplima.uxmessentials.moderation.application.port.SanctionSync.NONE,
                clock);
        Ban ban = new Ban(
                repository,
                new FakeSanctions(),
                guard,
                ModerationFakes.notifier(),
                audit,
                events,
                history,
                limit,
                ModerationFakes.broadcast(),
                com.uxplima.uxmessentials.moderation.application.port.SanctionSync.NONE,
                com.uxplima.uxmessentials.moderation.domain.AddressStrictness.NORMAL,
                new FakeIpHistoryStore(),
                clock);
        Kick kick = new Kick(
                new FakeSanctions(), guard, ModerationFakes.notifier(), audit, history, ModerationFakes.broadcast());
        return new WarnEscalator(
                com.uxplima.uxmessentials.moderation.domain.WarnEscalation.NONE,
                mute,
                tempBan,
                ban,
                kick,
                ModerationFakes.notifier());
    }
}
