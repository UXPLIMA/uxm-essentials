package com.uxplima.uxmessentials.teleport.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import com.uxplima.uxmessentials.teleport.adapter.outbound.InMemoryBackLocationStore;
import com.uxplima.uxmessentials.teleport.application.CaptureBack;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published teleport actions: the hop goes through the executor rather than around it, the future waits for
 * the landing, and a return nobody has a point for says so instead of moving them somewhere.
 */
class TeleportActionsTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final UxmLocation ARENA = new UxmLocation("world", 100, 64, -200);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);

    private RecordingExecutor executor;
    private InMemoryBackLocationStore backStore;
    private ActionDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        executor = new RecordingExecutor();
        backStore = new InMemoryBackLocationStore();
        scheduler = new ActionDoubles.InlineScheduler();
    }

    @Test
    void teleportingSendsThePlayerThroughTheExecutor() {
        UxmOutcome outcome = actions().teleport(ALICE.uuid(), ARENA).join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(executor.hops).containsExactly(TeleportKind.ADMIN);
        assertThat(executor.lastPosition().x()).isEqualTo(100);
    }

    @Test
    void theFutureWaitsForTheLandingRatherThanTheDispatch() {
        executor.holdTheLanding();

        var pending = actions().teleport(ALICE.uuid(), ARENA);

        assertThat(pending.isDone())
                .as("the hop was asked for, but the player has not arrived")
                .isFalse();
        executor.land();
        assertThat(pending.join().succeeded()).isTrue();
    }

    @Test
    void aWorldTheServerHasNotLoadedIsAFailureRatherThanAnException() {
        UxmOutcome outcome = actions()
                .teleport(ALICE.uuid(), new UxmLocation("nowhere", 0, 64, 0))
                .join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.NOT_FOUND)).isTrue();
        assertThat(executor.hops).isEmpty();
    }

    @Test
    void aPlayerWhoIsNotHereIsSaidToBeOffline() {
        UxmOutcome outcome = actions().teleport(UUID.randomUUID(), ARENA).join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.PLAYER_OFFLINE)).isTrue();
        assertThat(executor.hops).isEmpty();
    }

    @Test
    void returningAPlayerWithNowhereToGoBackToSaysSo() {
        UxmOutcome outcome = actions().back(ALICE.uuid()).join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.NOT_FOUND)).isTrue();
        assertThat(executor.hops).isEmpty();
    }

    @Test
    void returningAPlayerTakesThemBackWhereTheyWere() {
        actions().teleport(ALICE.uuid(), ARENA).join();

        UxmOutcome outcome = actions().back(ALICE.uuid()).join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(executor.hops).containsExactly(TeleportKind.ADMIN, TeleportKind.BACK);
    }

    private TeleportActions actions() {
        TeleportSettings settings = new TeleportSettings(new NoWaitConfig());
        TeleportEngine engine = new TeleportEngine(
                new NoCooldowns(),
                new ImmediateWarmups(),
                executor,
                ActionDoubles.silentNotifier(),
                new ActionDoubles.RecordingEvents(),
                settings,
                JailGate.NEVER);
        CaptureBack captureBack = new CaptureBack(
                backStore, engine, ActionDoubles.silentNotifier(), new ActionDoubles.RecordingEvents(), CLOCK);
        return new TeleportActions(
                executor,
                captureBack,
                settings,
                new QueryDoubles.MapLookup().with(ALICE),
                new ActionDoubles.NamedWorlds().with(WORLD),
                new NoPermissions(),
                scheduler);
    }

    /**
     * Stands in for the entity hop, and can be told to hold the landing so a test can see the difference between
     * a teleport that was asked for and one that happened.
     */
    private final class RecordingExecutor implements TeleportExecutor {

        private final List<TeleportKind> hops = new ArrayList<>();
        private final List<Position> positions = new ArrayList<>();
        private boolean hold;
        private @Nullable Runnable pendingLanding;

        void holdTheLanding() {
            hold = true;
        }

        void land() {
            Runnable landing = pendingLanding;
            pendingLanding = null;
            if (landing != null) {
                landing.run();
            }
        }

        Position lastPosition() {
            return positions.getLast();
        }

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            teleport(who, destination, kind, () -> {});
        }

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind, Runnable onLanded) {
            hops.add(kind);
            positions.add(destination.position());
            // The real executor captures the return point as part of the hop; this stands in for that too, so
            // /back has something to find afterwards.
            backStore.capture(
                    who,
                    com.uxplima.uxmessentials.teleport.domain.BackLocation.beforeTeleport(
                            destination.position(), CLOCK.instant()));
            if (hold) {
                pendingLanding = onLanded;
                return;
            }
            onLanded.run();
        }
    }

    private static final class NoCooldowns implements Cooldowns {

        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    /** Nothing to stand still for: the warmup completes the instant it begins. */
    private static final class ImmediateWarmups implements Warmups {

        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            onComplete.run();
            return new CompletedWarmup(who);
        }
    }

    private static final class NoPermissions implements Permissions {

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

    /** Every setting at its default, with no warmup or cooldown to wait out. */
    private record NoWaitConfig() implements ConfigStore {

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
            return "default-warmup".equals(path) || "default-cooldown".equals(path) ? 0 : fallback;
        }
    }
}
