package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * The warning ladder: the third warning mutes for an hour and the fifth bans for a day, each on the warning that
 * reaches it, while timed warnings that lapsed no longer count toward it. The day ban then holds the player off the
 * server until it runs out.
 *
 * <p>A rung that fires while a sanction of its kind still runs leaves the player under the stronger of the two: a
 * longer rung replaces the running mute or ban, a shorter one never cuts it short, and the staff member who gave the
 * warning is told which of those happened.
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

    private final RecordingSync sync = new RecordingSync();
    private final Backends backends = new Backends();
    private final Mute mute = new Mute(
            records,
            guard,
            notifier.notifier(),
            audit,
            events,
            history,
            limit,
            (key, placeholders) -> broadcasts.add(key),
            sync,
            clock);
    private final TempBan tempBan = new TempBan(
            records,
            online,
            guard,
            notifier.notifier(),
            audit,
            events,
            history,
            limit,
            (key, placeholders) -> broadcasts.add(key),
            sync,
            clock);
    private final Ban ban = new Ban(
            records,
            online,
            guard,
            notifier.notifier(),
            audit,
            events,
            history,
            limit,
            (key, placeholders) -> broadcasts.add(key),
            sync,
            AddressStrictness.NORMAL,
            new NoIpHistory(),
            clock);
    private final Kick kick = new Kick(
            online, guard, notifier.notifier(), audit, history, (key, placeholders) -> broadcasts.add(key), backends);

    private final WarnEscalator escalator = escalatorFor(LADDER);
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

    private WarnEscalator escalatorFor(WarnEscalation ladder) {
        return new WarnEscalator(ladder, mute, tempBan, ban, kick, notifier.notifier());
    }

    /** A {@code /warn} whose escalations follow {@code ladder} instead of the default one. */
    private IssueWarn warnUnder(WarnEscalation ladder) {
        return new IssueWarn(
                records,
                guard,
                notifier.notifier(),
                audit,
                events,
                history,
                (key, placeholders) -> broadcasts.add(key),
                escalatorFor(ladder),
                clock);
    }

    private static WarnEscalation ladder(EscalationStep... steps) {
        return new WarnEscalation(List.of(steps));
    }

    private static EscalationStep rung(int count, EscalationAction action, Duration span) {
        return new EscalationStep(count, action, Optional.of(span));
    }

    private static EscalationStep rung(int count, EscalationAction action) {
        return new EscalationStep(count, action, Optional.empty());
    }

    private static void warnTimes(IssueWarn issuer, int count) {
        for (int i = 0; i < count; i++) {
            issuer.warn(ALEX, SAM, Optional.of("spam"), false);
        }
    }

    private static final Set<String> ESCALATION_LINES = Set.of(
            ModerationMessageKey.MOD_WARN_ESCALATED.key(),
            ModerationMessageKey.MOD_WARN_ESCALATED_EXTENDED.key(),
            ModerationMessageKey.MOD_WARN_ESCALATED_HELD.key(),
            ModerationMessageKey.MOD_WARN_ESCALATION_FAILED.key());

    /** What the staff member who gave the warnings was told about each escalation, in order. */
    private List<String> toldAlex() {
        return notifier.sent().stream()
                .filter(line -> line.viewer().equals(ALEX))
                .map(RecordingNotifier.Sent::key)
                .filter(ESCALATION_LINES::contains)
                .toList();
    }

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

    @Test
    void aLongerMuteRungWhileTheEarlierMuteRunsExtendsIt() {
        IssueWarn tight = warnUnder(ladder(
                rung(2, EscalationAction.TEMPMUTE, Duration.ofMinutes(10)),
                rung(3, EscalationAction.TEMPMUTE, Duration.ofDays(1))));
        warnTimes(tight, 2);
        clock.advance(Duration.ofMinutes(4));

        warnTimes(tight, 1);

        assertThat(records.loadMute(SAM)).isInstanceOfSatisfying(MuteState.Timed.class, timed -> {
            assertThat(timed.until()).isEqualTo(clock.instant().plus(Duration.ofDays(1)));
            assertThat(timed.issuer().name()).isEqualTo("system");
        });
        assertThat(toldAlex())
                .containsExactly(
                        ModerationMessageKey.MOD_WARN_ESCALATED.key(),
                        ModerationMessageKey.MOD_WARN_ESCALATED_EXTENDED.key());
        assertThat(sync.mutesChanged).isEqualTo(2);
        assertThat(notifier.sent().stream()
                        .filter(line -> line.viewer().equals(SAM))
                        .filter(line -> line.key().equals(ModerationMessageKey.MUTE_NOTIFY_TARGET.key()))
                        .map(line -> line.placeholders().get("duration")))
                .containsExactly("10m", "1d");
        clock.advance(Duration.ofHours(23));
        assertThat(records.loadMute(SAM).isActiveAt(clock.instant())).isTrue();
    }

    @Test
    void aShorterMuteRungDoesNotShortenTheMuteThatRuns() {
        IssueWarn backwards = warnUnder(ladder(
                rung(2, EscalationAction.TEMPMUTE, Duration.ofDays(1)),
                rung(3, EscalationAction.TEMPMUTE, Duration.ofMinutes(10))));
        warnTimes(backwards, 2);
        clock.advance(Duration.ofMinutes(4));

        warnTimes(backwards, 1);

        assertThat(records.loadMute(SAM).expiry()).contains(NOW.plus(Duration.ofDays(1)));
        assertThat(toldAlex())
                .containsExactly(
                        ModerationMessageKey.MOD_WARN_ESCALATED.key(),
                        ModerationMessageKey.MOD_WARN_ESCALATED_HELD.key());
        assertThat(sync.mutesChanged).isEqualTo(1);
        assertThat(broadcasts).containsOnlyOnce(ModerationMessageKey.MOD_BROADCAST_MUTE);
    }

    @Test
    void aStaffPermanentMuteOutlastsATimedRung() {
        mute.mute(ALEX, SAM, "", Optional.of("slurs"), false);

        warnTimes(3, false);

        assertThat(records.loadMute(SAM)).isInstanceOfSatisfying(MuteState.Permanent.class, permanent -> {
            assertThat(permanent.issuer().name()).isEqualTo("Alex");
            assertThat(permanent.reason()).contains("slurs");
        });
        assertThat(toldAlex()).containsExactly(ModerationMessageKey.MOD_WARN_ESCALATED_HELD.key());
    }

    @Test
    void aPermanentMuteRungReplacesATimedMuteAndThenStays() {
        IssueWarn toPermanent = warnUnder(ladder(
                rung(2, EscalationAction.TEMPMUTE, Duration.ofMinutes(10)),
                rung(3, EscalationAction.MUTE),
                rung(4, EscalationAction.TEMPMUTE, Duration.ofDays(7))));
        warnTimes(toPermanent, 3);

        assertThat(records.loadMute(SAM)).isInstanceOf(MuteState.Permanent.class);

        warnTimes(toPermanent, 1);

        assertThat(records.loadMute(SAM)).isInstanceOf(MuteState.Permanent.class);
        assertThat(toldAlex())
                .containsExactly(
                        ModerationMessageKey.MOD_WARN_ESCALATED.key(),
                        ModerationMessageKey.MOD_WARN_ESCALATED_EXTENDED.key(),
                        ModerationMessageKey.MOD_WARN_ESCALATED_HELD.key());
    }

    @Test
    void aMuteThatRanOutMakesTheNextRungAFreshMute() {
        IssueWarn tight = warnUnder(ladder(
                rung(2, EscalationAction.TEMPMUTE, Duration.ofMinutes(10)),
                rung(3, EscalationAction.TEMPMUTE, Duration.ofDays(1))));
        warnTimes(tight, 2);
        clock.advance(Duration.ofMinutes(10));

        warnTimes(tight, 1);

        assertThat(records.loadMute(SAM).expiry()).contains(clock.instant().plus(Duration.ofDays(1)));
        assertThat(toldAlex())
                .containsExactly(
                        ModerationMessageKey.MOD_WARN_ESCALATED.key(), ModerationMessageKey.MOD_WARN_ESCALATED.key());
    }

    @Test
    void aLongerBanRungWhileTheEarlierBanRunsExtendsIt() {
        IssueWarn tight = warnUnder(ladder(
                rung(2, EscalationAction.TEMPBAN, Duration.ofHours(1)),
                rung(3, EscalationAction.TEMPBAN, Duration.ofDays(1))));
        warnTimes(tight, 2);
        clock.advance(Duration.ofMinutes(30));

        warnTimes(tight, 1);

        assertThat(records.loadTempban(SAM).expiry()).contains(clock.instant().plus(Duration.ofDays(1)));
        assertThat(toldAlex())
                .containsExactly(
                        ModerationMessageKey.MOD_WARN_ESCALATED.key(),
                        ModerationMessageKey.MOD_WARN_ESCALATED_EXTENDED.key());
        assertThat(sync.bansChanged).isEqualTo(2);
        clock.advance(Duration.ofHours(2));
        assertThat(login.evaluate(SAM, ADDRESS, false, false).allowed()).isFalse();
    }

    @Test
    void aShorterBanRungDoesNotShortenTheBanThatRuns() {
        IssueWarn backwards = warnUnder(ladder(
                rung(2, EscalationAction.TEMPBAN, Duration.ofDays(1)),
                rung(3, EscalationAction.TEMPBAN, Duration.ofHours(1))));
        warnTimes(backwards, 2);

        warnTimes(backwards, 1);

        assertThat(records.loadTempban(SAM).expiry()).contains(NOW.plus(Duration.ofDays(1)));
        assertThat(toldAlex())
                .containsExactly(
                        ModerationMessageKey.MOD_WARN_ESCALATED.key(),
                        ModerationMessageKey.MOD_WARN_ESCALATED_HELD.key());
        assertThat(sync.bansChanged).isEqualTo(1);
    }

    @Test
    void aStaffPermanentBanOutlastsATempbanRung() {
        ban.ban(ALEX, SAM, Optional.of("cheating"), false);
        clock.advance(Duration.ofDays(3));

        warnTimes(5, false);

        assertThat(records.loadTempban(SAM)).isInstanceOfSatisfying(TempbanState.Active.class, active -> {
            assertThat(active.until()).isEqualTo(NOW.plus(Ban.PERMANENT_SPAN));
            assertThat(active.issuer().name()).isEqualTo("Alex");
        });
        assertThat(toldAlex())
                .containsExactly(
                        ModerationMessageKey.MOD_WARN_ESCALATED.key(),
                        ModerationMessageKey.MOD_WARN_ESCALATED_HELD.key());
        assertThat(sync.bansChanged).isEqualTo(1);
    }

    @Test
    void aBanRungMakesATempbanPermanentAndALaterBanRungLeavesItBe() {
        IssueWarn toBan = warnUnder(ladder(
                rung(2, EscalationAction.TEMPBAN, Duration.ofDays(1)),
                rung(3, EscalationAction.BAN),
                rung(4, EscalationAction.BAN)));
        warnTimes(toBan, 3);
        Instant permanentUntil = clock.instant().plus(Ban.PERMANENT_SPAN);
        clock.advance(Duration.ofDays(2));

        warnTimes(toBan, 1);

        assertThat(records.loadTempban(SAM).expiry()).contains(permanentUntil);
        assertThat(toldAlex())
                .containsExactly(
                        ModerationMessageKey.MOD_WARN_ESCALATED.key(),
                        ModerationMessageKey.MOD_WARN_ESCALATED_EXTENDED.key(),
                        ModerationMessageKey.MOD_WARN_ESCALATED_HELD.key());
    }

    @Test
    void aKickRungThatCannotReachThePlayerIsReportedAsFailed() {
        IssueWarn kicking = warnUnder(ladder(rung(1, EscalationAction.KICK)));
        backends.connect(SAM, "lobby");

        warnTimes(kicking, 1);

        assertThat(online.kicked()).isEmpty();
        assertThat(toldAlex()).containsExactly(ModerationMessageKey.MOD_WARN_ESCALATION_FAILED.key());
    }

    /** Counts the cross-server frames a sanction sends, so a test can tell a replaced sanction from a kept one. */
    private static final class RecordingSync implements SanctionSync {

        private int bansChanged;
        private int mutesChanged;

        @Override
        public void banChanged(PlayerRef target) {
            bansChanged++;
        }

        @Override
        public void muteChanged(PlayerRef target) {
            mutesChanged++;
        }

        @Override
        public void ipBanChanged(String ip) {
            // No address bans on this path.
        }
    }

    /** This backend is "survival"; a player connected elsewhere is one a kick from here cannot reach. */
    private static final class Backends implements NetworkPresence {

        private final Map<UUID, OnlinePlayer> elsewhere = new HashMap<>();

        void connect(PlayerRef player, String server) {
            elsewhere.put(player.uuid(), new OnlinePlayer(player, server));
        }

        @Override
        public String localServer() {
            return "survival";
        }

        @Override
        public Optional<OnlinePlayer> find(UUID player) {
            return Optional.ofNullable(elsewhere.get(player));
        }

        @Override
        public Optional<OnlinePlayer> findByName(String name) {
            return elsewhere.values().stream()
                    .filter(found -> found.player().name().equalsIgnoreCase(name))
                    .findFirst();
        }

        @Override
        public List<OnlinePlayer> onServer(String server) {
            return elsewhere.values().stream()
                    .filter(found -> found.server().equals(server))
                    .toList();
        }

        @Override
        public List<OnlinePlayer> online() {
            return List.copyOf(elsewhere.values());
        }
    }
}
