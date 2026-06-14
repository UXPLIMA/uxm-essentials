package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.AddressStrictness;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctionHistory;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctions;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.moderation.fakes.RecordingModerationAudit;
import com.uxplima.uxmessentials.moderation.fakes.RecordingSanctionSync;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * A successful ban/tempban/mute announces the cross-server live effect through {@link
 * com.uxplima.uxmessentials.moderation.application.port.SanctionSync}; a refused one (exempt target, bad
 * duration) announces nothing. The frame is published only on the success path, after the durable write, so a
 * peer never reacts to a sanction that did not actually land.
 */
class SanctionSyncPublishTest {

    private static final Instant NOW = Instant.parse("2026-06-14T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "staff");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "griefer");

    @Test
    void banPublishesBanChangedOnSuccess() {
        RecordingSanctionSync sync = new RecordingSanctionSync();
        ban(ModerationFakes.exempt(), sync).ban(ACTOR, TARGET, Optional.of("griefing"), false);

        assertThat(sync.bans).containsExactly(TARGET);
        assertThat(sync.mutes).isEmpty();
    }

    @Test
    void banOfAnExemptTargetPublishesNothing() {
        RecordingSanctionSync sync = new RecordingSanctionSync();
        ban(ModerationFakes.exempt(TARGET.uuid()), sync).ban(ACTOR, TARGET, Optional.empty(), false);

        assertThat(sync.bans).isEmpty();
    }

    @Test
    void tempbanPublishesBanChangedOnSuccessButNotOnABadDuration() {
        RecordingSanctionSync sync = new RecordingSanctionSync();
        TempBan tempBan = tempBan(sync);

        tempBan.tempban(ACTOR, TARGET, "1d", Optional.of("cheating"), false);
        assertThat(sync.bans).containsExactly(TARGET);

        sync.bans.clear();
        tempBan.tempban(ACTOR, TARGET, "", Optional.empty(), false);
        assertThat(sync.bans).isEmpty();
    }

    @Test
    void mutePublishesMuteChangedOnSuccess() {
        RecordingSanctionSync sync = new RecordingSanctionSync();
        mute(sync).mute(ACTOR, TARGET, "1h", Optional.of("spam"), false);

        assertThat(sync.mutes).containsExactly(TARGET);
        assertThat(sync.bans).isEmpty();
    }

    private static Ban ban(
            com.uxplima.uxmessentials.shared.application.port.Permissions perms, RecordingSanctionSync sync) {
        FakeModerationRepository repository = new FakeModerationRepository();
        return new Ban(
                repository,
                new FakeSanctions(TARGET),
                new ModerationGuard(perms),
                ModerationFakes.notifier(),
                new RecordingModerationAudit(),
                new ModerationFakes.RecordingEvents(),
                new SanctionHistoryRecorder(new FakeSanctionHistory(), CLOCK),
                new SanctionDurationLimit(ModerationFakes.exempt()),
                ModerationFakes.broadcast(),
                sync,
                AddressStrictness.NORMAL,
                CLOCK);
    }

    private static TempBan tempBan(RecordingSanctionSync sync) {
        FakeModerationRepository repository = new FakeModerationRepository();
        return new TempBan(
                repository,
                new FakeSanctions(TARGET),
                new ModerationGuard(ModerationFakes.exempt()),
                ModerationFakes.notifier(),
                new RecordingModerationAudit(),
                new ModerationFakes.RecordingEvents(),
                new SanctionHistoryRecorder(new FakeSanctionHistory(), CLOCK),
                new SanctionDurationLimit(ModerationFakes.exempt()),
                ModerationFakes.broadcast(),
                sync,
                CLOCK);
    }

    private static Mute mute(RecordingSanctionSync sync) {
        FakeModerationRepository repository = new FakeModerationRepository();
        return new Mute(
                repository,
                new ModerationGuard(ModerationFakes.exempt()),
                ModerationFakes.notifier(),
                new RecordingModerationAudit(),
                new ModerationFakes.RecordingEvents(),
                new SanctionHistoryRecorder(new FakeSanctionHistory(), CLOCK),
                new SanctionDurationLimit(ModerationFakes.exempt()),
                ModerationFakes.broadcast(),
                sync,
                CLOCK);
    }
}
