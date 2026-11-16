package com.uxplima.uxmessentials.teleport.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.adapter.outbound.warmup.SchedulerWarmups;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.teleport.PaidTeleports;
import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.WarmupTracker;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.WarmupCancelReason;
import com.uxplima.uxmessentials.teleport.domain.event.WarmupCancelled;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Every way a teleport warmup is cut short, through the real engine, the tracking decorator, the kernel countdown
 * and the tracker the listeners feed, with every cancel toggle on: each cause tells the player its own line and names
 * itself on the cancel event, and a player who quits mid-warmup is cancelled for good, paid or not, even after a
 * fast relog.
 */
class WarmupCancelCausesTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000a1e5"), "Alex");
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-0000000000f1"), "world");
    private static final Position STANDING = new Position(WORLD, 10.5, 64, 10.5, 0f, 0f);
    private static final Position TURNED = new Position(WORLD, 10.5, 64, 10.5, 90f, 0f);
    private static final Position WALKED_OFF = new Position(WORLD, 14.5, 64, 10.5, 0f, 0f);
    private static final Position HOME = new Position(WORLD, 500, 70, 500, 0f, 0f);
    private static final Position MARKET = new Position(WORLD, -800, 70, 90, 0f, 0f);

    private static final String STARTED = "teleport.warmup.started";

    private final Countdowns scheduler = new Countdowns();
    private final List<String> told = new ArrayList<>();
    private final List<DomainEvent> published = new ArrayList<>();
    private final List<Position> landed = new ArrayList<>();
    private final WarmupTracker tracker = new WarmupTracker();
    private final PaidTeleports paid = new PaidTeleports(scheduler, new QuietLog());
    private final TeleportEngine engine = engine();
    private int refunds;

    private TeleportEngine engine() {
        Notifier notifier = new Notifier(
                (PlayerRef viewer, MessageKey key, Map<String, String> placeholders) -> {
                    told.add(key.key());
                    return key.key();
                },
                (viewer, text) -> {});
        TeleportSettings settings = new TeleportSettings(new EveryCancelOn());
        TrackingWarmups warmups = new TrackingWarmups(
                new SchedulerWarmups(scheduler, new NoNodes()),
                tracker,
                settings::cancelToggles,
                new NoNodes(),
                Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC),
                notifier,
                who -> Optional.of(STANDING),
                new QuietLog());
        return new TeleportEngine(
                new NoCooldowns(), warmups, new Landing(), notifier, published::add, settings, who -> false);
    }

    private void teleportTo(Position to) {
        assertThat(engine.launch(ALEX, Destination.at(to), TeleportKind.HOME).isOk())
                .isTrue();
    }

    /** A home paid for before its warmup: the charge is settled on how the teleport ends. */
    private List<TeleportOutcome> paidTeleportTo(Position to) {
        List<TeleportOutcome> outcomes = new ArrayList<>();
        PaidTeleports.Settlement settlement = paid.open(
                "kind=home",
                () -> {
                    refunds++;
                    return true;
                },
                () -> told.add("home.refunded"));
        CompletableFuture<TeleportOutcome> outcome = new CompletableFuture<>();
        engine.launch(ALEX, Destination.at(to), TeleportKind.HOME, ended -> {
            outcomes.add(ended);
            outcome.complete(ended);
        });
        settlement.follow(outcome);
        return outcomes;
    }

    private List<WarmupCancelReason> cancelReasons() {
        return published.stream()
                .filter(WarmupCancelled.class::isInstance)
                .map(event -> ((WarmupCancelled) event).reason())
                .toList();
    }

    @Test
    void walkingOffTheBlockSaysTheyMoved() {
        teleportTo(HOME);

        tracker.onMove(ALEX.uuid(), WALKED_OFF);
        scheduler.elapse();

        assertThat(told).containsExactly(STARTED, "teleport.warmup.cancelled");
        assertThat(cancelReasons()).containsExactly(WarmupCancelReason.MOVED);
        assertThat(landed).isEmpty();
    }

    @Test
    void turningOnTheSpotSaysTheyTurned() {
        teleportTo(HOME);

        tracker.onMove(ALEX.uuid(), TURNED);
        scheduler.elapse();

        assertThat(told).containsExactly(STARTED, "teleport.warmup.rotated");
        assertThat(cancelReasons()).containsExactly(WarmupCancelReason.ROTATED);
        assertThat(landed).isEmpty();
    }

    @Test
    void aHitSaysTheyTookDamage() {
        teleportTo(HOME);

        tracker.onAction(ALEX.uuid(), WarmupCancelReason.DAMAGED);
        scheduler.elapse();

        assertThat(told).containsExactly(STARTED, "teleport.warmup.damaged");
        assertThat(cancelReasons()).containsExactly(WarmupCancelReason.DAMAGED);
        assertThat(landed).isEmpty();
    }

    @Test
    void aClickSaysTheyInteracted() {
        teleportTo(HOME);

        tracker.onAction(ALEX.uuid(), WarmupCancelReason.INTERACTED);
        scheduler.elapse();

        assertThat(told).containsExactly(STARTED, "teleport.warmup.interacted");
        assertThat(cancelReasons()).containsExactly(WarmupCancelReason.INTERACTED);
        assertThat(landed).isEmpty();
    }

    @Test
    void aNewerTeleportSaysTheEarlierOneWasReplaced() {
        teleportTo(HOME);
        teleportTo(MARKET);

        scheduler.elapse();

        assertThat(told).containsExactly(STARTED, "teleport.warmup.replaced", STARTED);
        assertThat(cancelReasons()).containsExactly(WarmupCancelReason.SUPERSEDED);
        assertThat(landed).containsExactly(MARKET);
    }

    @Test
    void quittingCancelsTheWarmupSoNothingLandsAfterAFastRelog() {
        teleportTo(HOME);

        tracker.quit(ALEX.uuid());
        // The countdown reaches the player by uuid, and the relogged player is online again when it ends.
        scheduler.elapse();

        assertThat(landed).isEmpty();
        assertThat(told).containsExactly(STARTED, "teleport.warmup.quit");
        assertThat(cancelReasons()).containsExactly(WarmupCancelReason.QUIT);
        assertThat(tracker.isTracking(ALEX.uuid())).isFalse();
    }

    @Test
    void quittingDuringAPaidWarmupRefundsItExactlyOnce() {
        List<TeleportOutcome> outcomes = paidTeleportTo(HOME);

        tracker.quit(ALEX.uuid());
        tracker.quit(ALEX.uuid());
        tracker.onMove(ALEX.uuid(), WALKED_OFF);
        scheduler.elapse();

        assertThat(landed).isEmpty();
        assertThat(outcomes).containsExactly(TeleportOutcome.CANCELLED);
        assertThat(refunds).isEqualTo(1);
        assertThat(told).containsExactly(STARTED, "teleport.warmup.quit", "home.refunded");
    }

    @Test
    void aPlayerGoneWhenTheCountdownEndsIsCancelledAsAQuitAndRefunded() {
        List<TeleportOutcome> outcomes = paidTeleportTo(HOME);

        scheduler.offline = true;
        scheduler.elapse();

        assertThat(landed).isEmpty();
        assertThat(outcomes).containsExactly(TeleportOutcome.CANCELLED);
        assertThat(refunds).isEqualTo(1);
        assertThat(cancelReasons()).containsExactly(WarmupCancelReason.QUIT);
    }

    @Test
    void quittingAfterTheTeleportLandedKeepsTheChargeAndCancelsNothing() {
        List<TeleportOutcome> outcomes = paidTeleportTo(HOME);
        scheduler.elapse();

        tracker.quit(ALEX.uuid());

        assertThat(landed).containsExactly(HOME);
        assertThat(outcomes).containsExactly(TeleportOutcome.LANDED);
        assertThat(refunds).isZero();
        assertThat(cancelReasons()).isEmpty();
    }

    /**
     * Countdowns that end when the test says, with the player's own thread queued behind them. A player marked
     * offline is gone from the entity scheduler, so their work takes the retired branch.
     */
    private static final class Countdowns implements Scheduler {

        private final List<Runnable> countdowns = new ArrayList<>();
        private final Deque<Runnable> playerThread = new ArrayDeque<>();
        private boolean offline;

        void elapse() {
            List<Runnable> due = List.copyOf(countdowns);
            countdowns.clear();
            due.forEach(Runnable::run);
            Runnable next;
            while ((next = playerThread.pollFirst()) != null) {
                next.run();
            }
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            if (!offline) {
                playerThread.add(task);
            }
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
            playerThread.add(offline ? retired : task);
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            countdowns.add(task);
        }
    }

    /** An executor that lands every hop and keeps where it landed. */
    private final class Landing implements TeleportExecutor {

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            landed.add(destination.position());
        }
    }

    /** A player with no permission nodes, so every duration is the configured default. */
    private static final class NoNodes implements Permissions {

        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    /** Cooldowns that never hold anyone back, keeping the test on the warmup alone. */
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

    /** Teleport settings with a three second warmup and every cancel toggle on. */
    private static final class EveryCancelOn implements ConfigStore {

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return path.startsWith("warmup.cancel-on-") || fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return path.equals("default-warmup") ? 3 : fallback;
        }
    }

    /** A logger nobody reads. */
    private static final class QuietLog implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
