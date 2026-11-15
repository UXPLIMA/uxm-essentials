package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.domain.AddressStrictness;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation.EscalationAction;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation.EscalationStep;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * The warning ladder: the third warning mutes for an hour and the fifth bans for a day, each on the warning that
 * reaches it, while timed warnings that lapsed no longer count toward it. The day ban then holds the player off the
 * server until it runs out.
 */
class WarnEscalationTest {

    private static final String ADDRESS = "198.51.100.4";
    private static final WarnEscalation LADDER = new WarnEscalation(List.of(
            new EscalationStep(3, EscalationAction.TEMPMUTE, Optional.of(Duration.ofHours(1))),
            new EscalationStep(5, EscalationAction.TEMPBAN, Optional.of(Duration.ofDays(1)))));

    private final MutableClock clock = new MutableClock(NOW);
    private final SanctionRecords records = new SanctionRecords();
    private final OnlinePlayers online = new OnlinePlayers().connect(SAM, ADDRESS);
    private final FakePermissions permissions = new FakePermissions();
    private final ModerationGuard guard = new ModerationGuard(permissions);
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final RecordingEvents events = new RecordingEvents();
    private final SanctionHistoryRecorder history = new SanctionHistoryRecorder(new DiscardedHistory(), clock);
    private final SanctionDurationLimit limit = new SanctionDurationLimit(permissions);
    private final List<MessageKey> broadcasts = new ArrayList<>();
    private final SilentAudit audit = new SilentAudit();

    private final WarnEscalator escalator = new WarnEscalator(
            LADDER,
            new Mute(
                    records,
                    guard,
                    notifier.notifier(),
                    audit,
                    events,
                    history,
                    limit,
                    (key, placeholders) -> broadcasts.add(key),
                    SanctionSync.NONE,
                    clock),
            new TempBan(
                    records,
                    online,
                    guard,
                    notifier.notifier(),
                    audit,
                    events,
                    history,
                    limit,
                    (key, placeholders) -> broadcasts.add(key),
                    SanctionSync.NONE,
                    clock),
            new Ban(
                    records,
                    online,
                    guard,
                    notifier.notifier(),
                    audit,
                    events,
                    history,
                    limit,
                    (key, placeholders) -> broadcasts.add(key),
                    SanctionSync.NONE,
                    AddressStrictness.NORMAL,
                    new NoIpHistory(),
                    clock),
            new Kick(
                    online,
                    guard,
                    notifier.notifier(),
                    audit,
                    history,
                    (key, placeholders) -> broadcasts.add(key),
                    new NobodyElsewhere()),
            notifier.notifier());
    private final IssueWarn warn = new IssueWarn(
            records,
            guard,
            notifier.notifier(),
            audit,
            events,
            history,
            (key, placeholders) -> broadcasts.add(key),
            escalator,
            clock);
    private final TempWarn tempWarn = new TempWarn(
            records,
            guard,
            notifier.notifier(),
            audit,
            events,
            history,
            (key, placeholders) -> broadcasts.add(key),
            escalator,
            clock);
    private final LoginEnforcement login = new LoginEnforcement(
            records, new IpAlts(new NoIpHistory(), address -> address), notifier.notifier(), audit, clock);

    private void warnTimes(int count, boolean silent) {
        for (int i = 0; i < count; i++) {
            warn.warn(ALEX, SAM, Optional.of("spam"), silent);
        }
    }

    private long escalations() {
        return notifier.keys().stream()
                .filter(ModerationMessageKey.MOD_WARN_ESCALATED.key()::equals)
                .count();
    }

    @Test
    void theThirdWarningMutesForTheRungsSpanUnderTheServersName() {
        warnTimes(2, false);
        assertThat(records.loadMute(SAM).isActiveAt(clock.instant())).isFalse();

        warnTimes(1, false);

        MuteState mute = records.loadMute(SAM);
        assertThat(mute).isInstanceOfSatisfying(MuteState.Timed.class, timed -> {
            assertThat(timed.until()).isEqualTo(NOW.plus(Duration.ofHours(1)));
            assertThat(timed.issuer().name()).isEqualTo("system");
        });
        assertThat(escalations()).isEqualTo(1);
        clock.advance(Duration.ofHours(1));
        assertThat(records.loadMute(SAM).isActiveAt(clock.instant())).isFalse();
    }

    @Test
    void aWarningPastTheRungDoesNotFireItAgain() {
        warnTimes(3, false);
        clock.advance(Duration.ofHours(2));

        warnTimes(1, false);

        assertThat(escalations()).isEqualTo(1);
        assertThat(records.loadMute(SAM).isActiveAt(clock.instant())).isFalse();
    }

    @Test
    void timedWarningsThatLapsedDoNotCountTowardTheLadder() {
        tempWarn.warn(ALEX, SAM, "1h", Optional.empty(), false);
        tempWarn.warn(ALEX, SAM, "1h", Optional.empty(), false);
        clock.advance(Duration.ofHours(1).plusSeconds(1));

        warn.warn(ALEX, SAM, Optional.empty(), false);

        assertThat(records.allWarns(SAM)).hasSize(3);
        assertThat(escalations()).isZero();
        assertThat(records.loadMute(SAM)).isEqualTo(MuteState.none());
    }

    @Test
    void timedWarningsStillRunningDoCount() {
        tempWarn.warn(ALEX, SAM, "1h", Optional.empty(), false);
        tempWarn.warn(ALEX, SAM, "1h", Optional.empty(), false);
        clock.advance(Duration.ofMinutes(59));

        tempWarn.warn(ALEX, SAM, "1h", Optional.empty(), false);

        assertThat(escalations()).isEqualTo(1);
        assertThat(records.loadMute(SAM).isActiveAt(clock.instant())).isTrue();
    }

    @Test
    void theFifthWarningBansForADayAndTheLoginIsRefusedUntilItRunsOut() {
        warnTimes(3, false);
        clock.advance(Duration.ofHours(2));

        warnTimes(2, false);

        assertThat(online.kicked()).containsOnlyKeys(SAM);
        assertThat(records.loadTempban(SAM).expiry()).contains(clock.instant().plus(Duration.ofDays(1)));
        assertThat(login.evaluate(SAM, ADDRESS, false, false).allowed()).isFalse();

        clock.advance(Duration.ofDays(1).minusSeconds(1));
        assertThat(login.evaluate(SAM, ADDRESS, false, false).allowed()).isFalse();

        clock.advance(Duration.ofSeconds(1));
        assertThat(login.evaluate(SAM, ADDRESS, false, false).allowed()).isTrue();
    }

    @Test
    void aSilentWarningEscalatesSilently() {
        warnTimes(3, true);

        assertThat(escalations()).isEqualTo(1);
        assertThat(broadcasts).isEmpty();
    }

    @Test
    void aWarningThatIsNotSilentAnnouncesTheWarningsAndTheEscalatedMute() {
        warnTimes(3, false);

        assertThat(escalations()).isEqualTo(1);
        assertThat(broadcasts)
                .containsExactly(
                        ModerationMessageKey.MOD_BROADCAST_WARN,
                        ModerationMessageKey.MOD_BROADCAST_WARN,
                        ModerationMessageKey.MOD_BROADCAST_WARN,
                        ModerationMessageKey.MOD_BROADCAST_MUTE);
    }

    @Test
    void anExemptPlayerIsNeitherWarnedNorEscalated() {
        permissions.grant(SAM, ModerationGuard.EXEMPT_NODE);

        warnTimes(3, false);

        assertThat(records.allWarns(SAM)).isEmpty();
        assertThat(records.loadMute(SAM)).isEqualTo(MuteState.none());
        assertThat(notifier.keys())
                .containsOnly(ModerationError.TARGET_EXEMPT.messageKey().key());
    }

    @Test
    void aTempWarnNeedsASpan() {
        assertThat(tempWarn.warn(ALEX, SAM, "perm", Optional.empty(), false).errorOrThrow())
                .isEqualTo(ModerationError.BAD_DURATION);
        assertThat(tempWarn.warn(ALEX, SAM, "0m", Optional.empty(), false).errorOrThrow())
                .isEqualTo(ModerationError.BAD_DURATION);

        assertThat(records.allWarns(SAM)).isEmpty();
        assertThat(records.loadTempban(SAM)).isEqualTo(TempbanState.none());
    }

    /** Every target is either on this backend or offline, so a kick acts here. */
    private static final class NobodyElsewhere implements NetworkPresence {

        @Override
        public String localServer() {
            return "survival";
        }

        @Override
        public Optional<OnlinePlayer> find(UUID player) {
            return Optional.empty();
        }

        @Override
        public Optional<OnlinePlayer> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public List<OnlinePlayer> onServer(String server) {
            return List.of();
        }

        @Override
        public List<OnlinePlayer> online() {
            return List.of();
        }
    }
}
