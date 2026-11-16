package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.OnlineLookup;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * A login is checked in two steps: the pre-login reads storage off the tick, and the login decides on what it read
 * with the player's permissions, reading no storage. A login with nothing to decide on is refused and asked to join
 * again, never let in unchecked.
 */
class LoginGateTest {

    private static final String ADDRESS = "198.51.100.9";
    private static final String OTHER_ADDRESS = "198.51.100.10";

    private final MutableClock clock = new MutableClock(NOW);
    private final QueuedScheduler scheduler = new QueuedScheduler();
    private final SanctionRecords records = new SanctionRecords();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final RecordingLogger log = new RecordingLogger();
    private final LoginGate gate = new LoginGate(
            new LoginEnforcement(
                    records,
                    new IpAlts(new NoIpHistory(), address -> address),
                    notifier.notifier(),
                    new SilentAudit(),
                    clock),
            new StorageLane(scheduler, notifier.notifier(), new FakePermissions(), new OnlineLookup(), log),
            notifier.notifier(),
            log,
            clock);

    private void banSam() {
        records.saveTempban(
                SAM, TempbanState.active(NOW.plus(Duration.ofHours(1)), Issuer.of(ALEX), Optional.of("grief"), NOW));
    }

    @Test
    void aBannedPlayerIsRefusedAtLoginOnWhatThePreLoginReadWithoutReadingStorageAgain() {
        banSam();

        assertThat(gate.preLogin(SAM, ADDRESS)).isEmpty();
        records.goUnreachable();
        LoginEnforcement.Decision decision = gate.login(SAM, ADDRESS, false, false);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retry()).isFalse();
        assertThat(decision.kickReason()).contains(ModerationMessageKey.TEMPBAN_KICK.key());
    }

    @Test
    void aPlayerWithoutBansIsLetInAndTheirLoginIsRecordedOffTheTick() {
        gate.preLogin(SAM, ADDRESS);

        assertThat(gate.login(SAM, ADDRESS, false, false).allowed()).isTrue();
        assertThat(records.seen()).isEmpty();

        scheduler.runAll();

        assertThat(records.seen()).containsExactly(SAM.uuid());
    }

    @Test
    void anExemptPlayerOnABannedAddressIsLetInByTheirPermissionAtLogin() {
        records.saveIpBan(
                new IpBan(ADDRESS, Optional.empty(), Optional.empty(), Optional.empty(), Issuer.of(ALEX), NOW));
        gate.preLogin(SAM, ADDRESS);
        gate.preLogin(ALEX, ADDRESS);

        assertThat(gate.login(SAM, ADDRESS, false, true).allowed()).isTrue();
        assertThat(gate.login(ALEX, ADDRESS, false, false).kickReason())
                .contains(ModerationMessageKey.BANIP_KICK.key());
    }

    @Test
    void aLockdownLetsInOnlyTheBypassHolder() {
        records.setLockedDown(true);
        gate.preLogin(SAM, ADDRESS);
        gate.preLogin(ALEX, ADDRESS);

        assertThat(gate.login(SAM, ADDRESS, true, false).allowed()).isTrue();
        assertThat(gate.login(ALEX, ADDRESS, false, false).kickReason())
                .contains(ModerationMessageKey.MOD_LOCKDOWN_KICK.key());
    }

    @Test
    void aPreLoginWhoseReadFailsIsRefusedAndAskedToJoinAgain() {
        records.goUnreachable();

        assertThat(gate.preLogin(SAM, ADDRESS)).contains(ModerationMessageKey.MOD_LOGIN_UNCHECKED.key());
        assertThat(log.warnings()).hasSize(1);
    }

    @Test
    void aLoginWithNoPreLoginReadIsRefusedAndAskedToJoinAgain() {
        LoginEnforcement.Decision decision = gate.login(SAM, ADDRESS, false, false);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retry()).isTrue();
        assertThat(decision.kickReason()).contains(ModerationMessageKey.MOD_LOGIN_UNCHECKED.key());
    }

    @Test
    void aPreLoginReadServesOneLoginOnly() {
        gate.preLogin(SAM, ADDRESS);
        gate.login(SAM, ADDRESS, false, false);

        assertThat(gate.login(SAM, ADDRESS, false, false).retry()).isTrue();
    }

    @Test
    void aReadOlderThanTheHoldIsNotTrusted() {
        gate.preLogin(SAM, ADDRESS);
        clock.advance(LoginGate.HELD_FOR.plusSeconds(1));

        assertThat(gate.login(SAM, ADDRESS, false, false).retry()).isTrue();
    }

    @Test
    void aReadForgottenAfterARefusedPreLoginLetsNobodyIn() {
        gate.preLogin(SAM, ADDRESS);
        gate.forget(SAM.uuid());

        assertThat(gate.login(SAM, ADDRESS, false, false).retry()).isTrue();
    }

    @Test
    void aReadOfOneAddressDoesNotDecideALoginFromAnother() {
        records.saveIpBan(
                new IpBan(OTHER_ADDRESS, Optional.empty(), Optional.empty(), Optional.empty(), Issuer.of(ALEX), NOW));
        gate.preLogin(SAM, ADDRESS);

        LoginEnforcement.Decision decision = gate.login(SAM, OTHER_ADDRESS, false, false);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retry()).isTrue();
    }

    @Test
    void aNewPreLoginReplacesTheReadOfAnEarlierAttempt() {
        gate.preLogin(SAM, ADDRESS);
        banSam();
        gate.preLogin(SAM, ADDRESS);

        assertThat(gate.login(SAM, ADDRESS, false, false).kickReason())
                .contains(ModerationMessageKey.TEMPBAN_KICK.key());
    }
}
