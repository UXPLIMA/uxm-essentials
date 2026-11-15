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
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.adapter.outbound.warmup.SchedulerWarmups;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A home teleport asked for from the async lane, where the home use cases look the home up, through the real engine
 * and the tracking warmups that read where the player stands.
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
    private final List<Position> arrived = new ArrayList<>();
    private final TeleportHomeAdapter teleporter = new TeleportHomeAdapter(engine(), threads);

    private TeleportEngine engine() {
        Notifier notifier = new Notifier((viewer, key, placeholders) -> key.key(), (viewer, text) -> {});
        TeleportSettings settings = new TeleportSettings(new NoWarmup());
        TrackingWarmups warmups = new TrackingWarmups(
                new SchedulerWarmups(threads, new NoNodes()),
                new WarmupTracker(),
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

    /** Names the thread a task runs on, and holds the player's thread work until the test runs it. */
    private static final class Threads implements Scheduler {

        private final Deque<Runnable> playerThread = new ArrayDeque<>();
        private String current = "async";

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
            playerThread.add(task);
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
            task.run();
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

    /** Teleport settings at their defaults with no warmup, so the hop follows the start directly. */
    private record NoWarmup() implements ConfigStore {

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
            return path.equals("default-warmup") ? 0 : fallback;
        }
    }
}
