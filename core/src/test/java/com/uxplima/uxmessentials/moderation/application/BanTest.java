package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerTempbanned;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctionHistory;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctions;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.moderation.fakes.RecordingModerationAudit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The {@code /ban} and {@code /unban} use cases reuse the tempban row to express a permanent ban: a ban is a
 * {@link TempbanState.Active} whose expiry is far enough out that {@code isActiveAt} stays true for any
 * realistic login, so the existing ban-on-login listener bars reconnection unchanged. {@code /unban} clears
 * that row. A target with the exempt node is refused; an {@code /unban} of a player who is not banned is
 * refused.
 */
class BanTest {

    private static final Instant NOW = Instant.parse("2026-06-02T00:00:00Z");
    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "staff");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "griefer");

    @Test
    void banAppliesAnEffectivelyPermanentTempbanAndKicks() {
        FakeModerationRepository repository = new FakeModerationRepository();
        FakeSanctions sanctions = new FakeSanctions(TARGET);
        ModerationFakes.RecordingEvents events = new ModerationFakes.RecordingEvents();
        RecordingModerationAudit audit = new RecordingModerationAudit();
        FakeSanctionHistory history = new FakeSanctionHistory();
        Ban ban = new Ban(
                repository,
                sanctions,
                new ModerationGuard(ModerationFakes.exempt()),
                ModerationFakes.notifier(),
                audit,
                events,
                new SanctionHistoryRecorder(history, Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = ban.ban(ACTOR, TARGET, Optional.of("griefing"));

        assertThat(result.isOk()).isTrue();
        TempbanState stored = repository.loadTempban(TARGET);
        assertThat(stored).isInstanceOf(TempbanState.Active.class);
        assertThat(stored.isActiveAt(NOW)).isTrue();
        assertThat(stored.isActiveAt(NOW.plus(Duration.ofDays(100_000)))).isTrue();
        assertThat(sanctions.kicked).containsExactly(TARGET);
        assertThat(events.events).hasSize(1).first().isInstanceOf(PlayerTempbanned.class);
        assertThat(audit.lines).singleElement().isEqualTo(new RecordingModerationAudit.Line("player_tempban", true));
        // A successful permanent ban records exactly one BAN history row, no expiry.
        assertThat(history.appended).singleElement().satisfies(row -> {
            assertThat(row.action()).isEqualTo(com.uxplima.uxmessentials.moderation.domain.SanctionAction.BAN);
            assertThat(row.expiry()).isEmpty();
        });
    }

    @Test
    void banOfAnExemptTargetIsRefused() {
        FakeModerationRepository repository = new FakeModerationRepository();
        FakeSanctions sanctions = new FakeSanctions(TARGET);
        ModerationFakes.RecordingEvents events = new ModerationFakes.RecordingEvents();
        RecordingModerationAudit audit = new RecordingModerationAudit();
        FakeSanctionHistory history = new FakeSanctionHistory();
        Ban ban = new Ban(
                repository,
                sanctions,
                new ModerationGuard(ModerationFakes.exempt(TARGET.uuid())),
                ModerationFakes.notifier(),
                audit,
                events,
                new SanctionHistoryRecorder(history, Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = ban.ban(ACTOR, TARGET, Optional.empty());

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(ModerationError.TARGET_EXEMPT);
        assertThat(repository.loadTempban(TARGET)).isInstanceOf(TempbanState.None.class);
        assertThat(sanctions.kicked).isEmpty();
        assertThat(audit.lines).singleElement().isEqualTo(new RecordingModerationAudit.Line("player_tempban", false));
        // A refused ban records no history row.
        assertThat(history.appended).isEmpty();
    }

    @Test
    void unbanLiftsAnActiveBan() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.saveTempban(
                TARGET,
                TempbanState.active(NOW.plus(Duration.ofDays(365_000)), Issuer.of(ACTOR), Optional.empty(), NOW));
        RecordingModerationAudit audit = new RecordingModerationAudit();
        FakeSanctionHistory history = new FakeSanctionHistory();
        Unban unban = new Unban(
                repository,
                ModerationFakes.notifier(),
                audit,
                new SanctionHistoryRecorder(history, Clock.fixed(NOW, ZoneOffset.UTC)));

        var result = unban.unban(ACTOR, TARGET);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.loadTempban(TARGET)).isInstanceOf(TempbanState.None.class);
        assertThat(audit.lines).singleElement().isEqualTo(new RecordingModerationAudit.Line("player_unban", true));
        // A successful unban records exactly one UNBAN history row.
        assertThat(history.appended).singleElement().satisfies(row -> assertThat(row.action())
                .isEqualTo(com.uxplima.uxmessentials.moderation.domain.SanctionAction.UNBAN));
    }

    @Test
    void unbanOfANotBannedPlayerIsRefused() {
        FakeModerationRepository repository = new FakeModerationRepository();
        RecordingModerationAudit audit = new RecordingModerationAudit();
        FakeSanctionHistory history = new FakeSanctionHistory();
        Unban unban = new Unban(
                repository,
                ModerationFakes.notifier(),
                audit,
                new SanctionHistoryRecorder(history, Clock.fixed(NOW, ZoneOffset.UTC)));

        var result = unban.unban(ACTOR, TARGET);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(ModerationError.NOT_BANNED);
        assertThat(audit.lines).singleElement().isEqualTo(new RecordingModerationAudit.Line("player_unban", false));
        // A refused unban records no history row.
        assertThat(history.appended).isEmpty();
    }
}
