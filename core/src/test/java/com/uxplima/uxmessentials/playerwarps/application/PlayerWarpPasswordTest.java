package com.uxplima.uxmessentials.playerwarps.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.shared.application.hop.HopCharges;
import com.uxplima.uxmessentials.shared.application.permission.DestinationPermissions;
import com.uxplima.uxmessentials.shared.application.teleport.PaidTeleports;
import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * Wrong passwords on a PASSWORD warp are limited per player and warp with no setup at all: the shipped limit locks
 * the warp's password after a few wrong tries, and no permission tier can lift it.
 */
class PlayerWarpPasswordTest {

    private static final String LOCAL = "lobby";
    private static final PlayerWarpId VAULT_ID = PlayerWarpId.of(3);
    private static final PlayerWarpName VAULT = PlayerWarpName.of("vault");
    private static final PlayerWarpId CELLAR_ID = PlayerWarpId.of(4);
    private static final PlayerWarpName CELLAR = PlayerWarpName.of("cellar");
    private static final String SECRET = "hunter2";

    private final MutableClock clock = new MutableClock(NOW);
    private final QueuedScheduler scheduler = new QueuedScheduler();
    private final RecordingLogger log = new RecordingLogger();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final FakePermissions permissions = new FakePermissions();
    private final PlayerWarpFakes.Warps warps = new PlayerWarpFakes.Warps();
    private final PlayerWarpFakes.Passwords passwords = new PlayerWarpFakes.Passwords();
    private final Visits visits = new Visits();

    PlayerWarpPasswordTest() {
        warps.put(locked(VAULT_ID, VAULT));
        warps.put(locked(CELLAR_ID, CELLAR));
        passwords.set(VAULT_ID, SECRET);
        passwords.set(CELLAR_ID, SECRET);
    }

    private static PlayerWarp locked(PlayerWarpId id, PlayerWarpName name) {
        return PlayerWarp.create(SAM, "Sam", name, at(0, 64, 0), NOW).withId(id).withAccess(WarpAccess.PASSWORD, NOW);
    }

    private UsePlayerWarp use(PasswordAttempts attempts) {
        HopCharges charges = new HopCharges(notifier.notifier(), log);
        PlayerWarpHopResolver resolver = new PlayerWarpHopResolver(
                warps, visits, Optional.empty(), position -> true, permissions, notifier.notifier(), scheduler, LOCAL);
        return new UsePlayerWarp(
                warps,
                visits,
                notifier.notifier(),
                position -> true,
                permissions,
                new PlayerWarpFakes.NoBans(),
                new PlayerWarpFakes.NoMembers(),
                new PlayerWarpFakes.NoWhitelist(),
                passwords,
                attempts,
                Optional.empty(),
                LOCAL,
                new PlayerWarpHops(Optional.empty(), charges, resolver, notifier.notifier()),
                clock,
                new PaidTeleports(scheduler, log),
                log);
    }

    private UsePlayerWarp shipped() {
        return use(PasswordAttempts.defaults(clock));
    }

    private static Result<Unit, PlayerWarpError> tryPassword(UsePlayerWarp use, PlayerWarpName warp, String password) {
        return use.useFor(ALEX, warp, Optional.of(password));
    }

    @Test
    void theShippedLimitLocksThePasswordAfterThreeWrongTries() {
        UsePlayerWarp use = shipped();
        assertThat(tryPassword(use, VAULT, "a").asError()).contains(PlayerWarpError.WRONG_PASSWORD);
        assertThat(tryPassword(use, VAULT, "b").asError()).contains(PlayerWarpError.WRONG_PASSWORD);
        assertThat(tryPassword(use, VAULT, "c").asError()).contains(PlayerWarpError.WRONG_PASSWORD);

        Result<Unit, PlayerWarpError> right = tryPassword(use, VAULT, SECRET);

        assertThat(right.asError()).as("even the right password waits").contains(PlayerWarpError.RATE_LIMITED);
        assertThat(visits.made).isEmpty();
    }

    @Test
    void theLockoutEndsAfterItsTime() {
        UsePlayerWarp use = shipped();
        tryPassword(use, VAULT, "a");
        tryPassword(use, VAULT, "b");
        tryPassword(use, VAULT, "c");

        clock.advance(PasswordAttempts.DEFAULT_LOCKOUT.minusSeconds(1));
        assertThat(tryPassword(use, VAULT, SECRET).asError()).contains(PlayerWarpError.RATE_LIMITED);
        clock.advance(Duration.ofSeconds(1));

        assertThat(tryPassword(use, VAULT, SECRET).isOk()).isTrue();
        assertThat(visits.made).containsExactly("Alex->vault");
    }

    @Test
    void aRightPasswordStartsTheCountAgain() {
        UsePlayerWarp use = shipped();
        tryPassword(use, VAULT, "a");
        tryPassword(use, VAULT, "b");
        tryPassword(use, VAULT, SECRET);

        assertThat(tryPassword(use, VAULT, "c").asError()).contains(PlayerWarpError.WRONG_PASSWORD);
        assertThat(tryPassword(use, VAULT, "d").asError()).contains(PlayerWarpError.WRONG_PASSWORD);
    }

    @Test
    void wrongTriesSpreadPastTheWindowNeverLock() {
        UsePlayerWarp use = shipped();
        tryPassword(use, VAULT, "a");
        tryPassword(use, VAULT, "b");
        clock.advance(PasswordAttempts.DEFAULT_LOCKOUT);

        assertThat(tryPassword(use, VAULT, "c").asError()).contains(PlayerWarpError.WRONG_PASSWORD);
        assertThat(tryPassword(use, VAULT, SECRET).isOk()).isTrue();
    }

    @Test
    void aLockedPasswordHoldsBackOnlyThatWarp() {
        UsePlayerWarp use = shipped();
        tryPassword(use, VAULT, "a");
        tryPassword(use, VAULT, "b");
        tryPassword(use, VAULT, "c");

        assertThat(tryPassword(use, CELLAR, SECRET).isOk()).isTrue();
    }

    @Test
    void aCooldownTierCannotLiftTheLimit() {
        permissions.holdTier(ALEX, "uxmessentials.cooldown.pwarp-password:3", 0);
        permissions.holdTier(ALEX, "uxmessentials.tp.cooldown", 0);
        UsePlayerWarp use = shipped();
        tryPassword(use, VAULT, "a");
        tryPassword(use, VAULT, "b");
        tryPassword(use, VAULT, "c");

        assertThat(tryPassword(use, VAULT, SECRET).asError()).contains(PlayerWarpError.RATE_LIMITED);
    }

    @Test
    void thePasswordBypassSkipsThePasswordAndItsLimit() {
        UsePlayerWarp use = shipped();
        tryPassword(use, VAULT, "a");
        tryPassword(use, VAULT, "b");
        tryPassword(use, VAULT, "c");
        permissions.grant(ALEX, DestinationPermissions.PWARP_BYPASS_PASSWORD);

        assertThat(tryPassword(use, VAULT, "wrong").isOk()).isTrue();
    }

    @Test
    void anOperatorSetsHowManyTriesAndHowLong() {
        UsePlayerWarp use = use(new PasswordAttempts(1, Duration.ofMinutes(5), clock));
        tryPassword(use, VAULT, "a");

        clock.advance(Duration.ofSeconds(299));
        assertThat(tryPassword(use, VAULT, SECRET).asError()).contains(PlayerWarpError.RATE_LIMITED);
        clock.advance(Duration.ofSeconds(1));
        assertThat(tryPassword(use, VAULT, SECRET).isOk()).isTrue();
    }

    @Test
    void aLockoutOfZeroTurnsTheLimitOff() {
        UsePlayerWarp use = use(new PasswordAttempts(1, Duration.ZERO, clock));
        for (int i = 0; i < 10; i++) {
            tryPassword(use, VAULT, "wrong" + i);
        }

        assertThat(tryPassword(use, VAULT, SECRET).isOk()).isTrue();
    }

    /** Lands every local teleport at once, keeping who it moved where. */
    private static final class Visits implements PlayerWarpTeleporter {

        private final List<String> made = new ArrayList<>();

        @Override
        public boolean admits(PlayerRef who, PlayerWarp warp) {
            return true;
        }

        @Override
        public CompletionStage<TeleportOutcome> teleportTo(PlayerRef who, PlayerWarp warp) {
            made.add(who.name() + "->" + warp.name().value());
            return CompletableFuture.completedFuture(TeleportOutcome.LANDED);
        }

        @Override
        public void arrive(PlayerRef who, PlayerWarp warp, Runnable onLanded, Runnable onMissed) {
            made.add(who.name() + "->" + warp.name().value());
            onLanded.run();
        }
    }
}
