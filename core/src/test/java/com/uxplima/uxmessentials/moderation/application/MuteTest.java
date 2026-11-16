package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * A staff mute never replaces a mute already in force, longer or shorter: the staff member is told which mute runs
 * and for how long, even when that mute lands in the moment between the read and the write.
 */
class MuteTest {

    private static final PlayerRef ROBIN =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000b0"), "Robin");

    private final MutableClock clock = new MutableClock(NOW);
    private final SanctionRecords records = new SanctionRecords();
    private final FakePermissions permissions = new FakePermissions();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final List<MessageKey> broadcasts = new ArrayList<>();
    private final List<PlayerRef> syncedMutes = new ArrayList<>();
    private final Mute mute = new Mute(
            records,
            new ModerationGuard(permissions),
            notifier.notifier(),
            new SilentAudit(),
            new RecordingEvents(),
            new SanctionHistoryRecorder(new DiscardedHistory(), clock),
            new SanctionDurationLimit(permissions),
            (key, placeholders) -> broadcasts.add(key),
            new SanctionSync() {
                @Override
                public void banChanged(PlayerRef target) {}

                @Override
                public void muteChanged(PlayerRef target) {
                    syncedMutes.add(target);
                }

                @Override
                public void ipBanChanged(String ip) {}
            },
            clock);

    /** The last line {@code viewer} was told. */
    private RecordingNotifier.Sent lastToldTo(PlayerRef viewer) {
        List<RecordingNotifier.Sent> told = notifier.sent().stream()
                .filter(line -> line.viewer().equals(viewer))
                .toList();
        return told.getLast();
    }

    @Test
    void aMuteOnAPermanentlyMutedPlayerIsRefusedAndNamesThePermanentMute() {
        mute.mute(ROBIN, SAM, "", Optional.of("slurs"), false);

        assertThat(mute.mute(ALEX, SAM, "1h", Optional.empty(), false).errorOrThrow())
                .isEqualTo(ModerationError.ALREADY_MUTED);

        assertThat(lastToldTo(ALEX))
                .isEqualTo(new RecordingNotifier.Sent(
                        ALEX, ModerationMessageKey.MUTE_ALREADY_PERMANENT.key(), Map.of("player", "Sam")));
        assertThat(records.loadMute(SAM))
                .isInstanceOfSatisfying(
                        MuteState.Permanent.class,
                        permanent -> assertThat(permanent.issuer().name()).isEqualTo("Robin"));
    }

    @Test
    void aMuteOnATimedMuteIsRefusedWithTheTimeItStillRuns() {
        mute.mute(ROBIN, SAM, "1d", Optional.empty(), false);
        clock.advance(Duration.ofHours(1));

        assertThat(mute.mute(ALEX, SAM, "", Optional.empty(), false).errorOrThrow())
                .isEqualTo(ModerationError.ALREADY_MUTED);

        assertThat(lastToldTo(ALEX))
                .isEqualTo(new RecordingNotifier.Sent(
                        ALEX,
                        ModerationMessageKey.MUTE_ALREADY_TIMED.key(),
                        Map.of("player", "Sam", "remaining", "23h")));
        assertThat(records.loadMute(SAM).expiry()).contains(NOW.plus(Duration.ofDays(1)));
    }

    @Test
    void aMuteThatRanOutHoldsNothingBack() {
        mute.mute(ROBIN, SAM, "1h", Optional.empty(), false);
        clock.advance(Duration.ofHours(1));

        assertThat(mute.mute(ALEX, SAM, "1d", Optional.empty(), false).isErr()).isFalse();

        assertThat(records.loadMute(SAM).expiry()).contains(clock.instant().plus(Duration.ofDays(1)));
    }

    @Test
    void aMuteLandingBetweenTheReadAndTheWriteIsKeptAndTheLaterOneRefused() {
        records.beforeNextReplace(() -> mute.mute(ROBIN, SAM, "", Optional.of("slurs"), true));

        assertThat(mute.mute(ALEX, SAM, "1h", Optional.empty(), false).errorOrThrow())
                .isEqualTo(ModerationError.ALREADY_MUTED);

        assertThat(records.loadMute(SAM))
                .isInstanceOfSatisfying(
                        MuteState.Permanent.class,
                        permanent -> assertThat(permanent.reason()).contains("slurs"));
        assertThat(lastToldTo(ALEX).key()).isEqualTo(ModerationMessageKey.MUTE_ALREADY_PERMANENT.key());
        assertThat(syncedMutes).containsExactly(SAM);
        assertThat(broadcasts).isEmpty();
    }

    @Test
    void aMuteWriteThatLosesEveryRaceGivesUpAndChangesNothing() {
        records.loseEveryReplace();

        assertThatThrownBy(() -> mute.mute(ALEX, SAM, "1h", Optional.empty(), false))
                .isInstanceOf(IllegalStateException.class);

        assertThat(records.loadMute(SAM)).isEqualTo(MuteState.none());
        assertThat(syncedMutes).isEmpty();
    }
}
