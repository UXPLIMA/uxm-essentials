package com.uxplima.uxmessentials.homes.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.adapter.outbound.warmup.SchedulerWarmups;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.WarmupTracker;
import com.uxplima.uxmessentials.teleport.adapter.outbound.TrackingWarmups;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A home teleport asked for from the async lane, where the home use cases look the home up, through the real engine
 * and the tracking warmups that read where the player stands, and the outcome it reports to the use case that paid.
 */
class TeleportHomeAdapterTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000a1e5"), "Alex");
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-0000000000f1"), "world");
    private static final Position STANDING = new Position(WORLD, 10.5, 64, 10.5, 0f, 0f);
    private static final Position HOME = new Position(WORLD, 500, 70, 500, 0f, 0f);

    private final Threads threads = new Threads();
    private final List<String> locatedOn = new ArrayList<>();
    private final List<Position> arrived = new CopyOnWriteArrayList<>();
    private final WarmupTracker tracker = new WarmupTracker();
    private final TeleportHomeAdapter teleporter = new TeleportHomeAdapter(engine(0), threads);

    private TeleportEngine engine(int warmupSeconds) {
        Notifier notifier = new Notifier((viewer, key, placeholders) -> key.key(), (viewer, text) -> {});
        TeleportSettings settings = new TeleportSettings(new Warmup(warmupSeconds));
        TrackingWarmups warmups = new TrackingWarmups(
                new SchedulerWarmups(threads, new NoNodes()),
                tracker,
                settings::cancelToggles,
                new NoNodes(),
                Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC),
                notifier,
                who -> {
                    locatedOn.add(threads.current);
                    return Optional.of(STANDING);
                });
        TeleportExecutor hops = (who, destination, kind) -> arrived.add(destination.position());
        return new TeleportEngine(new NoCooldowns(), warmups, hops, notifier, event -> {}, settings, who -> false);
    }

    private static Home home() {
        return Home.create(ALEX, HomeSlot.of(0), HOME, Instant.parse("2026-11-15T12:00:00Z"));
    }

    @Test
    void aTeleportAskedForOffThePlayersThreadReadsWhereTheyStandOnlyOnTheirThread() {
        threads.current = "async";

        teleporter.teleportTo(ALEX, home());

        assertThat(locatedOn)
                .as("nothing reads the player's position on the async lane")
                .isEmpty();

        threads.runPlayerThread();

        assertThat(locatedOn).containsExactly("player");
        assertThat(arrived).containsExactly(HOME);
    }

    @Test
    void aTeleportAskedForOnThePlayersThreadStartsAtOnce() {
        threads.current = "player";

        teleporter.teleportTo(ALEX, home());

        assertThat(locatedOn).containsExactly("player");
        threads.runPlayerThread();
        assertThat(arrived).containsExactly(HOME);
    }

    @Test
    void aTeleportThatArrivesReportsALanding() {
        threads.current = "async";

        CompletionStage<TeleportOutcome> outcome = teleporter.teleportTo(ALEX, home());
        assertThat(outcome.toCompletableFuture()).isNotDone();
        threads.runPlayerThread();

        assertThat(outcome.toCompletableFuture()).isCompletedWithValue(TeleportOutcome.LANDED);
    }

    @Test
    void aPlayerGoneBeforeTheTeleportReachedTheirThreadIsReportedAsRetired() {
        threads.current = "async";
        threads.gone = true;

        CompletionStage<TeleportOutcome> outcome = teleporter.teleportTo(ALEX, home());

        assertThat(outcome.toCompletableFuture()).isCompletedWithValue(TeleportOutcome.RETIRED);
        assertThat(locatedOn).isEmpty();
        assertThat(arrived).isEmpty();
    }

    @Test
    void aPlayerWhoLeavesDuringTheWarmupIsReportedAsCancelled() {
        TeleportHomeAdapter warming = new TeleportHomeAdapter(engine(3), threads);
        threads.current = "player";
        CompletionStage<TeleportOutcome> outcome = warming.teleportTo(ALEX, home());
        assertThat(outcome.toCompletableFuture()).isNotDone();

        threads.gone = true;
        threads.runDelayed();

        assertThat(outcome.toCompletableFuture()).isCompletedWithValue(TeleportOutcome.CANCELLED);
        assertThat(arrived).isEmpty();
    }

    @Test
    void aMoveDuringTheWarmupIsReportedAsCancelled() {
        TeleportHomeAdapter warming = new TeleportHomeAdapter(engine(3), threads);
        threads.current = "player";
        CompletionStage<TeleportOutcome> outcome = warming.teleportTo(ALEX, home());

        tracker.onMove(ALEX.uuid(), HOME);
        threads.runDelayed();

        assertThat(outcome.toCompletableFuture()).isCompletedWithValue(TeleportOutcome.CANCELLED);
        assertThat(arrived).isEmpty();
    }

    @Test
    void aLandingAndAMoveRacingOnTwoThreadsReportExactlyOneOutcome() throws Exception {
        threads.current = "player";
        threads.inline = true;
        TeleportEngine engine = engine(3);
        for (int round = 0; round < 300; round++) {
            arrived.clear();
            List<TeleportOutcome> outcomes = new CopyOnWriteArrayList<>();
            engine.launch(ALEX, Destination.at(HOME), TeleportKind.HOME, outcomes::add);
            Runnable countdownEnds = threads.takeDelayed();
            CountDownLatch start = new CountDownLatch(1);
            Thread lands = new Thread(() -> awaitThen(start, countdownEnds));
            Thread moves = new Thread(() -> awaitThen(start, () -> tracker.onMove(ALEX.uuid(), HOME)));
            lands.start();
            moves.start();
            start.countDown();
            lands.join();
            moves.join();

            assertThat(outcomes).hasSize(1);
            assertThat(outcomes.get(0)).isIn(TeleportOutcome.LANDED, TeleportOutcome.CANCELLED);
            assertThat(arrived)
                    .as("the player moved exactly when the landing was reported")
                    .hasSize(outcomes.get(0) == TeleportOutcome.LANDED ? 1 : 0);
        }
    }

    private static void awaitThen(CountDownLatch start, Runnable work) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        work.run();
    }

    /**
     * Names the thread a task runs on, and holds the player's thread work and every countdown until the test runs
     * it. A player who is gone has their thread work retired, as the entity scheduler does.
     */
    private static final class Threads implements Scheduler {

        private final Deque<Runnable> playerThread = new ArrayDeque<>();
        private final Deque<Runnable> delayed = new ArrayDeque<>();
        private String current = "async";
        private volatile boolean gone;
        // Runs the player's thread work where it is asked for, for the test that races two threads.
        private volatile boolean inline;

        void runDelayed() {
            Runnable next;
            while ((next = delayed.pollFirst()) != null) {
                next.run();
            }
        }

        Runnable takeDelayed() {
            return Objects.requireNonNull(delayed.pollFirst(), "no countdown is waiting");
        }

        void runPlayerThread() {
            Runnable next;
            while ((next = playerThread.pollFirst()) != null) {
                String before = current;
                current = "player";
                try {
                    next.run();
                } finally {
                    current = before;
                }
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
            onEntity(player, task, () -> {});
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
            if (gone) {
                retired.run();
            } else if (inline) {
                task.run();
            } else {
                playerThread.add(task);
            }
        }

        @Override
        public boolean ownsEntity(PlayerRef player) {
            return current.equals("player");
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            delayed.add(task);
        }
    }

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

    /** Teleport settings at their defaults with the given warmup; none makes the hop follow the start directly. */
    private record Warmup(int seconds) implements ConfigStore {

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
            return path.equals("default-warmup") ? seconds : fallback;
        }
    }
}
