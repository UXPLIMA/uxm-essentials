package com.uxplima.uxmessentials.warps.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.warps.adapter.WarpTeleportRegistry;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.Test;

/** A warp teleport asked for from the async lane, and what the use case that paid for it hears back. */
class TeleportWarpAdapterTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000a1e5"), "Alex");
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-0000000000f1"), "world");
    private static final Position SHOP = new Position(WORLD, 40, 64, 40, 0f, 0f);

    private final PlayerThread thread = new PlayerThread();
    private final WarpTeleportRegistry registry = new WarpTeleportRegistry();
    private final List<Position> arrived = new ArrayList<>();
    private final TeleportWarpAdapter teleporter = new TeleportWarpAdapter(engine(), registry, thread);

    private TeleportEngine engine() {
        Notifier notifier = new Notifier((viewer, key, placeholders) -> key.key(), (viewer, text) -> {});
        Warmups noWarmup = (who, kind, onComplete, onCancel) -> {
            onComplete.run();
            return new Warmups.CompletedWarmup(who);
        };
        return new TeleportEngine(
                new NoCooldowns(),
                noWarmup,
                (who, destination, kind) -> arrived.add(destination.position()),
                notifier,
                event -> {},
                new TeleportSettings(new Defaults()),
                who -> false);
    }

    private static Warp shop() {
        return Warp.create(WarpName.of("shop"), SHOP, ALEX, Instant.parse("2026-11-15T12:00:00Z"));
    }

    @Test
    void aWarpTeleportThatArrivesReportsALanding() {
        CompletionStage<TeleportOutcome> outcome = teleporter.teleportTo(ALEX, shop());
        thread.run();

        assertThat(outcome.toCompletableFuture()).isCompletedWithValue(TeleportOutcome.LANDED);
        assertThat(arrived).containsExactly(SHOP);
    }

    @Test
    void aPlayerGoneBeforeTheWarpTeleportStartedIsReportedAsRetiredAndTheirArrivalNoticeIsDropped() {
        thread.gone = true;

        CompletionStage<TeleportOutcome> outcome = teleporter.teleportTo(ALEX, shop());

        assertThat(outcome.toCompletableFuture()).isCompletedWithValue(TeleportOutcome.RETIRED);
        assertThat(registry.getAndRemove(ALEX.uuid())).isEmpty();
        assertThat(arrived).isEmpty();
    }

    /** The player's thread, reached by one hop from the async lane; a player who is gone retires the hop. */
    private static final class PlayerThread implements Scheduler {

        private final List<Runnable> waiting = new ArrayList<>();
        private boolean gone;

        void run() {
            List<Runnable> due = List.copyOf(waiting);
            waiting.clear();
            due.forEach(Runnable::run);
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
            } else {
                waiting.add(task);
            }
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
