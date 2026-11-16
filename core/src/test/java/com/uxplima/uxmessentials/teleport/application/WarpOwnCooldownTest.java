package com.uxplima.uxmessentials.teleport.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpTimingOverrides;
import com.uxplima.uxmessentials.shared.application.ledger.NetworkClaims;
import com.uxplima.uxmessentials.shared.application.ledger.NetworkCooldowns;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.Fixtures;
import com.uxplima.uxmessentials.testing.MemoryClaimLedger;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Each warp that sets its own cooldown in the editor runs it on a clock of its own, kept in the network's shared
 * cooldown store, and a player's teleport cooldown tier can only lower that cooldown.
 */
class WarpOwnCooldownTest {

    private static final String TP_TIER = "uxmessentials.tp.cooldown";

    private final MutableClock clock = new MutableClock(NOW);
    private final QueuedScheduler scheduler = new QueuedScheduler();
    private final NetworkClaims claims =
            new NetworkClaims(new MemoryClaimLedger(), scheduler, clock, new RecordingLogger());
    private final FakePermissions permissions = new FakePermissions();
    private final Cooldowns cooldowns = new NetworkCooldowns(claims, permissions, clock);
    private final TeleportFakes.HeldWarmups warmups = new TeleportFakes.HeldWarmups();
    private final TeleportEngine engine = new TeleportEngine(
            cooldowns,
            warmups,
            new TeleportFakes.Hops(),
            new RecordingNotifier().notifier(),
            new RecordingEvents(),
            new TeleportSettings(TeleportFakes.Config.of("default-cooldown", "30")),
            who -> false);

    @BeforeEach
    void alexIsRead() {
        claims.joined(ALEX.uuid());
        scheduler.runAll();
    }

    private static Destination toWarp(String name, double cooldownSeconds) {
        Warp warp = Warp.create(WarpName.of(name), Fixtures.at(0, 64, 0), SAM, NOW)
                .withCooldownOverride(Optional.of(cooldownSeconds));
        return Destination.at(warp.location(), Optional.empty(), warp.ownCooldown());
    }

    private void land(Destination destination) {
        assertThat(engine.launch(ALEX, destination, TeleportKind.WARP).isOk()).isTrue();
        warmups.finish();
        scheduler.runAll();
    }

    private boolean refused(Destination destination) {
        return engine.launch(ALEX, destination, TeleportKind.WARP)
                .asError()
                .equals(Optional.of(TeleportError.ON_COOLDOWN));
    }

    @Test
    void aWarpWithALongCooldownDoesNotHoldBackAWarpWithAShortOne() {
        Destination arena = toWarp("arena", 60);
        Destination shop = toWarp("shop", 5);

        land(arena);

        assertThat(refused(arena)).isTrue();
        assertThat(refused(shop)).as("the shop's clock never started").isFalse();
    }

    @Test
    void eachWarpWaitsOutItsOwnCooldown() {
        Destination arena = toWarp("arena", 60);
        Destination shop = toWarp("shop", 5);
        land(arena);
        land(shop);

        clock.advance(Duration.ofSeconds(5));

        assertThat(refused(shop)).isFalse();
        assertThat(refused(arena)).isTrue();
        clock.advance(Duration.ofSeconds(55));
        assertThat(refused(arena)).isFalse();
    }

    @Test
    void aWarpWithItsOwnCooldownLeavesTheSharedTeleportClockAlone() {
        land(toWarp("arena", 60));

        assertThat(engine.launch(ALEX, Destination.at(Fixtures.at(5, 64, 5)), TeleportKind.HOME)
                        .isOk())
                .isTrue();
    }

    @Test
    void aTeleportTierLowersAWarpsOwnCooldown() {
        permissions.holdTier(ALEX, TP_TIER, 10);
        Destination arena = toWarp("arena", 60);
        land(arena);

        clock.advance(Duration.ofSeconds(10));

        assertThat(refused(arena)).isFalse();
    }

    @Test
    void aTeleportTierNeverRaisesAWarpsOwnCooldown() {
        permissions.holdTier(ALEX, TP_TIER, 120);
        Destination shop = toWarp("shop", 5);
        land(shop);

        clock.advance(Duration.ofSeconds(5));

        assertThat(refused(shop)).isFalse();
    }

    @Test
    void aPlayerWarpKeepsItsClockThroughARename() {
        PlayerWarp market = PlayerWarp.create(SAM, "Sam", PlayerWarpName.of("market"), Fixtures.at(0, 64, 0), NOW)
                .withId(PlayerWarpId.of(7))
                .withTiming(new WarpTimingOverrides(Optional.empty(), Optional.of(60.0)), NOW);
        PlayerWarp renamed = market.renamed(PlayerWarpName.of("bazaar"), NOW);
        land(Destination.at(market.location(), Optional.empty(), market.ownCooldown()));

        assertThat(refused(Destination.at(renamed.location(), Optional.empty(), renamed.ownCooldown())))
                .isTrue();
    }

    @Test
    void aPlayerWarpAndAServerWarpOfTheSameNameKeepApartClocks() {
        PlayerWarp market = PlayerWarp.create(SAM, "Sam", PlayerWarpName.of("market"), Fixtures.at(0, 64, 0), NOW)
                .withId(PlayerWarpId.of(7))
                .withTiming(new WarpTimingOverrides(Optional.empty(), Optional.of(60.0)), NOW);
        land(Destination.at(market.location(), Optional.empty(), market.ownCooldown()));

        assertThat(refused(toWarp("market", 60))).isFalse();
    }
}
