package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.domain.AddressStrictness;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.application.permission.EnforcementPermissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * A banned player reads the permanent ban screen for a permanent ban and the timed one, with the time still left, for
 * a timed ban: when the ban kicks them, when another server of the network kicks them, and at every later login.
 */
class BanScreenTest {

    private static final String ADDRESS = "198.51.100.9";
    private static final PlayerRef JO = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000000b"), "Jo");

    private final MutableClock clock = new MutableClock(NOW);
    private final SanctionRecords records = new SanctionRecords();
    private final OnlinePlayers here = new OnlinePlayers().connect(SAM, ADDRESS);
    private final OnlinePlayers elsewhere = new OnlinePlayers().connect(SAM, ADDRESS);
    private final FakePermissions permissions = new FakePermissions();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final SanctionSync noSync = new SanctionSync() {
        @Override
        public void banChanged(PlayerRef target) {}

        @Override
        public void muteChanged(PlayerRef target) {}

        @Override
        public void ipBanChanged(String ip) {}
    };
    private final Ban ban = new Ban(
            records,
            here,
            new ModerationGuard(permissions),
            notifier.notifier(),
            new SilentAudit(),
            new RecordingEvents(),
            new SanctionHistoryRecorder(new DiscardedHistory(), clock),
            new SanctionDurationLimit(permissions),
            (key, placeholders) -> {},
            noSync,
            AddressStrictness.NORMAL,
            new NoIpHistory(),
            clock);
    private final TempBan tempBan = new TempBan(
            records,
            here,
            new ModerationGuard(permissions),
            notifier.notifier(),
            new SilentAudit(),
            new RecordingEvents(),
            new SanctionHistoryRecorder(new DiscardedHistory(), clock),
            new SanctionDurationLimit(permissions),
            (key, placeholders) -> {},
            noSync,
            clock);
    private final LoginEnforcement login = new LoginEnforcement(
            records, new IpAlts(new NoIpHistory(), address -> address), notifier.notifier(), new SilentAudit(), clock);
    private final EnforceRemoteBan otherServer = new EnforceRemoteBan(records, elsewhere, notifier.notifier(), clock);

    /** The screens Sam was shown, oldest first. */
    private List<RecordingNotifier.Sent> screensOfSam() {
        return notifier.sent().stream()
                .filter(line -> line.viewer().equals(SAM))
                .toList();
    }

    private static RecordingNotifier.Sent permanentScreen(String reason) {
        return new RecordingNotifier.Sent(SAM, ModerationMessageKey.BAN_KICK.key(), Map.of("reason", reason));
    }

    private static RecordingNotifier.Sent timedScreen(String left, String reason) {
        return new RecordingNotifier.Sent(
                SAM, ModerationMessageKey.TEMPBAN_KICK.key(), Map.of("duration", left, "reason", reason));
    }

    @Test
    void aPermanentBanShowsThePermanentScreenWhenItKicksAndAtEveryLaterLogin() {
        ban.ban(ALEX, SAM, Optional.of("cheating"), false);
        clock.advance(Duration.ofDays(30));

        LoginEnforcement.Decision decision = Logins.check(login, SAM, ADDRESS, false, false);

        assertThat(here.kicked()).containsEntry(SAM, ModerationMessageKey.BAN_KICK.key());
        assertThat(decision.kickReason()).contains(ModerationMessageKey.BAN_KICK.key());
        assertThat(screensOfSam()).containsExactly(permanentScreen("cheating"), permanentScreen("cheating"));
    }

    @Test
    void anotherServerKicksAPermanentlyBannedPlayerWithThePermanentScreen() {
        ban.ban(ALEX, SAM, Optional.empty(), false);

        otherServer.onRemoteBan(SAM.uuid());

        assertThat(elsewhere.kicked()).containsEntry(SAM, ModerationMessageKey.BAN_KICK.key());
        assertThat(screensOfSam()).containsExactly(permanentScreen(""), permanentScreen(""));
    }

    @Test
    void aTimedBanStillShowsTheTimeLeftAtLoginAndOnAnotherServer() {
        tempBan.tempban(ALEX, SAM, "2h", Optional.of("grief"), false);
        clock.advance(Duration.ofMinutes(30));

        Logins.check(login, SAM, ADDRESS, false, false);
        otherServer.onRemoteBan(SAM.uuid());

        assertThat(elsewhere.kicked()).containsEntry(SAM, ModerationMessageKey.TEMPBAN_KICK.key());
        assertThat(screensOfSam())
                .containsExactly(
                        timedScreen("2h", "grief"), timedScreen("1h30m", "grief"), timedScreen("1h30m", "grief"));
    }

    @Test
    void aBanShortenedByTheIssuersCapShowsTheTimedScreen() {
        permissions.quota(
                EnforcementPermissions.maxDuration("ban"),
                QuotaResult.limited(Duration.ofHours(1).toSeconds()));

        ban.ban(ALEX, SAM, Optional.of("spam"), false);
        clock.advance(Duration.ofMinutes(15));
        Logins.check(login, SAM, ADDRESS, false, false);

        assertThat(screensOfSam()).containsExactly(timedScreen("1h", "spam"), timedScreen("45m", "spam"));
    }

    @Test
    void aPermanentBanIsReadOutwardAsOneWithNoEnd() {
        ban.ban(ALEX, SAM, Optional.empty(), false);
        TempbanState.Active permanent = (TempbanState.Active) records.loadTempban(SAM);
        tempBan.tempban(ALEX, JO, "1d", Optional.empty(), false);
        TempbanState.Active timed = (TempbanState.Active) records.loadTempban(JO);

        assertThat(Ban.endOf(permanent)).isEmpty();
        assertThat(Ban.endOf(timed)).contains(NOW.plus(Duration.ofDays(1)));
    }
}
