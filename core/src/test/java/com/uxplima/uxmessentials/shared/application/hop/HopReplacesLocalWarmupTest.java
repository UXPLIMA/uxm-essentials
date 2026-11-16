package com.uxplima.uxmessentials.shared.application.hop;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.application.hop.port.HopConnector;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.teleport.PaidTeleports;
import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.WarmupCancelReason;
import com.uxplima.uxmessentials.teleport.domain.event.WarmupCancelled;
import com.uxplima.uxmessentials.testing.ManualWarmups;
import com.uxplima.uxmessentials.testing.MemoryHopStore;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.testing.SteppedScheduler;
import org.junit.jupiter.api.Test;

/**
 * A player who starts a paid local {@code /home} and, while it warms up, picks a warp on another backend. The hop is
 * a new teleport, so the home must not land behind it and its charge comes back once; a hop that never leaves keeps
 * the home waiting.
 */
class HopReplacesLocalWarmupTest {

    private static final String LOBBY = "lobby";
    private static final String SURVIVAL = "survival";
    private static final Position HOME = at(300, 70, -40);
    private static final Duration TTL = Duration.ofSeconds(30);

    private final MutableClock clock = new MutableClock(NOW);
    private final SteppedScheduler scheduler = new SteppedScheduler(clock);
    private final RecordingLogger log = new RecordingLogger();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final RecordingEvents events = new RecordingEvents();
    private final ManualWarmups warmups = new ManualWarmups();
    private final List<Position> landed = new ArrayList<>();
    private final List<String> order = new ArrayList<>();
    private final PaidTeleports paid = new PaidTeleports(scheduler, log);
    private final TeleportEngine engine = new TeleportEngine(
            new NoCooldowns(),
            warmups,
            new Landing(),
            notifier.notifier(),
            events,
            new TeleportSettings(new Defaults()),
            who -> false);
    private final HopCharges charges = new HopCharges(notifier.notifier(), log);
    private final HopResolvers resolvers = new HopResolvers();
    private final HopDepartures lobby;
    private int homeRefunds;
    private boolean gateRefuses;
    private boolean proxyAvailable = true;

    HopReplacesLocalWarmupTest() {
        scheduler.online(ALEX);
        resolvers.register(new Warps());
        HopWatchdog watchdog = new HopWatchdog(new MemoryHopStore(), resolvers, charges, scheduler, log, TTL, clock);
        lobby = new HopDepartures(
                (who, departure) -> !gateRefuses,
                new Proxy(),
                watchdog,
                charges,
                resolvers,
                scheduler,
                LOBBY,
                clock,
                who -> {
                    order.add("supersede");
                    warmups.supersede(who);
                });
    }

    /** What a paid home does: take the charge, open its settlement, then launch the warmup the settlement follows. */
    private void paidHome() {
        PaidTeleports.Settlement settlement = paid.open(
                "kind=home",
                () -> {
                    homeRefunds++;
                    return true;
                },
                () -> notifier.notifier().send(ALEX, () -> "home.refunded"));
        CompletableFuture<TeleportOutcome> outcome = new CompletableFuture<>();
        engine.launch(ALEX, Destination.at(HOME), TeleportKind.HOME, outcome::complete);
        settlement.follow(outcome);
    }

    /** What a warp on another backend does: ask the gate, then send the player with the charge it took. */
    private boolean warpElsewhere() {
        HopDeparture departure =
                new HopDeparture(HopKind.WARP, "market", SURVIVAL, "market", at(0, 64, 0), Optional.empty());
        if (!lobby.admit(ALEX, departure)) {
            return false;
        }
        lobby.send(ALEX, departure, Optional.of(new HopCharge(BigDecimal.TEN, "coins")), Optional.empty());
        return true;
    }

    private List<WarmupCancelReason> cancelReasons() {
        return events.published().stream()
                .filter(WarmupCancelled.class::isInstance)
                .map(event -> ((WarmupCancelled) event).reason())
                .toList();
    }

    @Test
    void aHopSentDuringAPaidHomeWarmupReplacesItAndRefundsItOnce() {
        paidHome();

        assertThat(warpElsewhere()).isTrue();
        warmups.finish(ALEX);
        warmups.move(ALEX);

        assertThat(order)
                .as("the home warmup ends before the player is handed to the proxy")
                .containsExactly("supersede", "connect");
        assertThat(landed).as("the home does not land behind the hop").isEmpty();
        assertThat(homeRefunds).isEqualTo(1);
        assertThat(cancelReasons()).containsExactly(WarmupCancelReason.SUPERSEDED);
        assertThat(notifier.keys()).containsExactly("warp.hop.sending", "teleport.warmup.replaced", "home.refunded");
    }

    @Test
    void aHopTheGateRefusesLeavesTheHomeWarmupToLand() {
        paidHome();
        gateRefuses = true;

        assertThat(warpElsewhere()).isFalse();
        warmups.finish(ALEX);

        assertThat(order).isEmpty();
        assertThat(landed).containsExactly(HOME);
        assertThat(homeRefunds).isZero();
        assertThat(cancelReasons()).isEmpty();
    }

    @Test
    void aHopThatCannotLeaveForWantOfAProxyLeavesTheHomeWarmupToLand() {
        paidHome();
        proxyAvailable = false;

        warpElsewhere();
        warmups.finish(ALEX);

        assertThat(order).isEmpty();
        assertThat(landed).containsExactly(HOME);
        assertThat(homeRefunds).isZero();
    }

    /** The proxy channel: available unless the test says not, recording each connect in order. */
    private final class Proxy implements HopConnector {

        @Override
        public boolean isAvailable() {
            return proxyAvailable;
        }

        @Override
        public void connect(PlayerRef player, String server) {
            order.add("connect");
        }
    }

    /** The warp kind's resolver on this backend: only the origin's notices and refunds are exercised. */
    private static final class Warps implements HopResolver {

        @Override
        public HopKind kind() {
            return HopKind.WARP;
        }

        @Override
        public HopNotices notices() {
            Map<HopNotice, MessageKey> keys = new EnumMap<>(HopNotice.class);
            for (HopNotice notice : HopNotice.values()) {
                String key =
                        "warp.hop." + notice.name().toLowerCase(Locale.ROOT).replace('_', '-');
                keys.put(notice, () -> key);
            }
            return new HopNotices("warp", keys);
        }

        @Override
        public String label(PendingHop hop) {
            return hop.target();
        }

        @Override
        public HopResolution resolve(PendingHop hop) {
            return HopResolution.unreachable(hop.target());
        }

        @Override
        public boolean refund(PlayerRef who, HopCharge charge) {
            return true;
        }

        @Override
        public boolean settle(PlayerRef who, HopSettlement settlement, HopCharge charge) {
            return true;
        }
    }

    /** An executor that lands every local hop. */
    private final class Landing implements TeleportExecutor {

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            landed.add(destination.position());
        }
    }

    /** Cooldowns that never hold anyone back. */
    private static final class NoCooldowns implements Cooldowns {

        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {
            // Nothing to remember.
        }

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {
            // Nothing to remember.
        }
    }

    /** Teleport settings at their defaults. */
    private static final class Defaults implements ConfigStore {

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }
}
