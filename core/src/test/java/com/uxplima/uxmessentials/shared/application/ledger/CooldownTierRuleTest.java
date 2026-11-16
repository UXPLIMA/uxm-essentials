package com.uxplima.uxmessentials.shared.application.ledger;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.uxplima.uxmessentials.poses.application.PoseCooldown;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownKind;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownStartPhase;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MemoryClaimLedger;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A held cooldown tier sets the wait, the shortest held wins, and the configured wait only fills in for no tier. */
class CooldownTierRuleTest {

    private static final CooldownKind TELEPORT = CooldownKind.onTeleport("tp", 5);

    private final MutableClock clock = new MutableClock(NOW);
    private final QueuedScheduler scheduler = new QueuedScheduler();
    private final NetworkClaims claims =
            new NetworkClaims(new MemoryClaimLedger(), scheduler, clock, new RecordingLogger());
    private final FakePermissions permissions = new FakePermissions();
    private final NetworkCooldowns cooldowns = new NetworkCooldowns(claims, permissions, clock);

    @BeforeEach
    void alexIsRead() {
        claims.joined(ALEX.uuid());
        scheduler.runAll();
    }

    private Duration waitAfterStamp(CooldownKind kind) {
        cooldowns.stamp(ALEX, kind);
        scheduler.runAll();
        return cooldowns.check(ALEX, kind).asError().orElse(Duration.ZERO);
    }

    @Test
    void aPosesTierAddsAWaitTheConfigLeavesOut() {
        permissions.holdTier(ALEX, "uxmessentials.poses.cooldown", 30);
        PoseCooldown poses = PoseCooldown.backedBy(cooldowns);

        poses.stamp(ALEX);
        scheduler.runAll();

        assertThat(poses.remaining(ALEX)).contains(Duration.ofSeconds(30));
    }

    @Test
    void withNoPosesTierThereIsNoWait() {
        PoseCooldown poses = PoseCooldown.backedBy(cooldowns);

        poses.stamp(ALEX);
        scheduler.runAll();

        assertThat(poses.remaining(ALEX)).isEmpty();
    }

    @Test
    void aTierLongerThanTheConfiguredWaitSetsIt() {
        permissions.holdTier(ALEX, "uxmessentials.tp.cooldown", 30);

        assertThat(waitAfterStamp(TELEPORT)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void theShortestHeldTierWins() {
        permissions.holdTier(ALEX, "uxmessentials.tp.cooldown", 30).holdTier(ALEX, "uxmessentials.tp.cooldown", 10);

        assertThat(waitAfterStamp(TELEPORT)).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void withNoTierTheConfiguredWaitApplies() {
        assertThat(waitAfterStamp(TELEPORT)).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void aZeroTierRemovesTheWait() {
        permissions.holdTier(ALEX, "uxmessentials.tp.cooldown", 0);

        assertThat(waitAfterStamp(TELEPORT)).isZero();
    }

    @Test
    void theBypassStillWinsOverAHeldTier() {
        permissions.holdTier(ALEX, "uxmessentials.tp.cooldown", 30).grant(ALEX, "uxmessentials.bypass.cooldown.tp");

        assertThat(waitAfterStamp(TELEPORT)).isZero();
    }

    @Test
    void aTierSharedByWaitsConfiguredApartOnlyLowersThem() {
        permissions.holdTier(ALEX, "uxmessentials.tp.cooldown", 30);
        CooldownKind freeBack = CooldownKind.scoped("tp", "tp.back", 0, CooldownStartPhase.TELEPORT);
        CooldownKind slowWarp = CooldownKind.scoped("tp", "tp.warp", 60, CooldownStartPhase.TELEPORT);

        assertThat(waitAfterStamp(freeBack)).isZero();
        assertThat(waitAfterStamp(slowWarp)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void aTierTooLongToCountInMillisStillHoldsThePlayer() {
        permissions.holdTier(ALEX, "uxmessentials.tp.cooldown", Long.MAX_VALUE / 10);

        assertThat(waitAfterStamp(TELEPORT)).isGreaterThan(Duration.ofDays(365L * 1000));
    }

    @Test
    void aLabelTierSetsTheWaitOverTheDeclaredOne() {
        permissions.holdTier(ALEX, "uxmessentials.cooldown.customcommand.daily", 30);

        cooldowns.stampLabel(ALEX, "customcommand.daily", Duration.ofSeconds(10));
        scheduler.runAll();

        assertThat(cooldowns.checkLabel(ALEX, "customcommand.daily").asError()).contains(Duration.ofSeconds(30));
    }
}
