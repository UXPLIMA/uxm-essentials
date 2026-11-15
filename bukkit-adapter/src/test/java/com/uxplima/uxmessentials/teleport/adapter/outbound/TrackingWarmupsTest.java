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

import com.uxplima.uxmessentials.shared.adapter.outbound.warmup.SchedulerWarmups;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
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
import com.uxplima.uxmessentials.teleport.domain.event.WarmupCancelled;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A player who starts a second teleport while the first is still warming up, through the real engine, the tracking
 * decorator, the kernel countdown and the tracker the move listener reads.
 */
class TrackingWarmupsTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000a1e5"), "Alex");
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-0000000000f1"), "world");
    private static final Position STANDING = new Position(WORLD, 10.5, 64, 10.5, 0f, 0f);
    private static final Position FIRST_HOME = new Position(WORLD, 500, 70, 500, 0f, 0f);
    private static final Position SECOND_HOME = new Position(WORLD, -800, 70, 90, 0f, 0f);
    private static final Position THIRD_HOME = new Position(WORLD, 40, 70, -1200, 0f, 0f);
    private static final Position WALKED_OFF = new Position(WORLD, 14.5, 64, 10.5, 0f, 0f);

    private static final String STARTED = "teleport.warmup.started";
    private static final String REPLACED = "teleport.warmup.replaced";
    private static final String MOVED = "teleport.warmup.cancelled";

    private final Countdowns scheduler = new Countdowns();
    private final List<String> told = new ArrayList<>();
    private final List<DomainEvent> published = new ArrayList<>();
    private final Hops hops = new Hops();
    private final WarmupTracker tracker = new WarmupTracker();
    private final TeleportEngine engine = engineWithWarmup("3");

    private TeleportEngine engineWithWarmup(String seconds) {
        Notifier notifier = new Notifier(
                (PlayerRef viewer, MessageKey key, Map<String, String> placeholders) -> {
                    told.add(key.key());
                    return key.key();
                },
                (viewer, text) -> {});
        TeleportSettings settings = new TeleportSettings(new Config(seconds));
        TrackingWarmups warmups = new TrackingWarmups(
                new SchedulerWarmups(scheduler, new NoNodes()),
                tracker,
                settings::cancelToggles,
                new NoNodes(),
                Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC),
                notifier,
                who -> Optional.of(STANDING),
                new QuietLog());
        return new TeleportEngine(new NoCooldowns(), warmups, hops, notifier, published::add, settings, who -> false);
    }

    private void teleportHome(Position home) {
        assertThat(engine.launch(ALEX, Destination.at(home), TeleportKind.HOME).isOk())
                .isTrue();
    }

    private long cancelledWarmups() {
        return published.stream().filter(WarmupCancelled.class::isInstance).count();
    }

    @Test
    void aSecondTeleportDuringTheWarmupCancelsTheFirstAndOnlyTheSecondLands() {
        teleportHome(FIRST_HOME);
        teleportHome(SECOND_HOME);

        scheduler.elapse();

        assertThat(hops.arrived).containsExactly(SECOND_HOME);
        assertThat(told)
                .as("the player hears the earlier teleport was replaced, not that they moved")
                .containsExactly(STARTED, REPLACED, STARTED);
        assertThat(cancelledWarmups())
                .as("the first warmup's cancel ran exactly once")
                .isEqualTo(1);
    }

    @Test
    void walkingOffAfterTheSecondTeleportStopsItAndNothingLands() {
        teleportHome(FIRST_HOME);
        teleportHome(SECOND_HOME);

        tracker.onMove(ALEX.uuid(), WALKED_OFF);
        scheduler.elapse();

        assertThat(hops.arrived)
                .as("the first warmup was not left running out of the move listener's reach")
                .isEmpty();
        assertThat(told).containsExactly(STARTED, REPLACED, STARTED, MOVED);
        assertThat(cancelledWarmups())
                .as("one cancel for each warmup, none run twice")
                .isEqualTo(2);
    }

    @Test
    void eachRepeatReplacesOnlyTheWarmupBeforeItAndTheLastOneLands() {
        teleportHome(FIRST_HOME);
        teleportHome(SECOND_HOME);
        teleportHome(THIRD_HOME);

        scheduler.elapse();

        assertThat(hops.arrived).containsExactly(THIRD_HOME);
        assertThat(told).containsExactly(STARTED, REPLACED, STARTED, REPLACED, STARTED);
        assertThat(cancelledWarmups()).isEqualTo(2);
    }

    @Test
    void aWarmupWhoseCountdownEndedButHasNotLandedYetLosesToTheReplacement() {
        teleportHome(FIRST_HOME);
        scheduler.elapseCountdownsOnly();

        teleportHome(SECOND_HOME);
        scheduler.elapse();

        assertThat(hops.arrived).containsExactly(SECOND_HOME);
        assertThat(cancelledWarmups()).isEqualTo(1);
    }

    @Test
    void aWarmupThatLandedBeforeTheNextTeleportIsNotCancelledAndTheNextStaysCancellable() {
        teleportHome(FIRST_HOME);
        scheduler.elapse();

        teleportHome(SECOND_HOME);
        tracker.onMove(ALEX.uuid(), WALKED_OFF);
        scheduler.elapse();

        assertThat(hops.arrived).containsExactly(FIRST_HOME);
        assertThat(told).containsExactly(STARTED, STARTED, MOVED);
        assertThat(cancelledWarmups()).isEqualTo(1);
    }

    @Test
    void aTeleportThatSkipsItsWarmupStillStopsTheEarlierOne() {
        teleportHome(FIRST_HOME);
        TeleportEngine instant = engineWithWarmup("0");

        assertThat(instant.launch(ALEX, Destination.at(SECOND_HOME), TeleportKind.HOME)
                        .isOk())
                .isTrue();
        scheduler.elapse();

        assertThat(hops.arrived)
                .as("the earlier warmup does not land after the instant one")
                .containsExactly(SECOND_HOME);
        assertThat(told).containsExactly(STARTED, REPLACED);
        assertThat(cancelledWarmups()).isEqualTo(1);
    }

    /**
     * A scheduler with the countdown split from the landing: {@link #elapseCountdownsOnly()} ends every countdown
     * and leaves the hop to the player's thread queued, the moment a replacement can still race the landing.
     */
    private static final class Countdowns implements Scheduler {

        private final List<Runnable> countdowns = new ArrayList<>();
        private final Deque<Runnable> playerThread = new ArrayDeque<>();

        void elapseCountdownsOnly() {
            List<Runnable> due = List.copyOf(countdowns);
            countdowns.clear();
            due.forEach(Runnable::run);
        }

        void elapse() {
            elapseCountdownsOnly();
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
            playerThread.add(task);
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

    /** An executor that lands every hop and keeps where it landed. */
    private static final class Hops implements TeleportExecutor {

        private final List<Position> arrived = new ArrayList<>();

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            arrived.add(destination.position());
        }
    }

    /** Teleport settings at their defaults, with the warmup length set. */
    private record Config(String warmupSeconds) implements ConfigStore {

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
            return path.equals("default-warmup") ? Integer.parseInt(warmupSeconds) : fallback;
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
