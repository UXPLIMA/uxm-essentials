package com.uxplima.uxmessentials.playerstate.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.bukkit.GameMode;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.BukkitNearbyPlayers;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.BukkitPlayerEffects;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.BukkitStateReconciler;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.InMemoryPlayerStateStore;
import com.uxplima.uxmessentials.playerstate.application.port.NearbyPlayers;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerStateStore;
import com.uxplima.uxmessentials.playerstate.application.port.StateReconciler;
import com.uxplima.uxmessentials.playerstate.domain.GameModeRef;
import com.uxplima.uxmessentials.playerstate.domain.PersonalTime;
import com.uxplima.uxmessentials.playerstate.domain.PlayerStateSnapshot;
import com.uxplima.uxmessentials.playerstate.domain.SpeedValue;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the playerstate outbound adapters against a real (mock) Bukkit server: the
 * {@link BukkitStateReconciler} pushing a snapshot's flags onto the live player, the {@link BukkitPlayerEffects}
 * apply-once/live-only verbs, the in-memory store's {@code compute}-based mutation, and the
 * {@link BukkitNearbyPlayers} scan. The {@link Scheduler} is a synchronous inline fake so the entity-thread
 * hop the adapters route through is exercised deterministically — on Folia the same call lands on the owning
 * region thread, but the unit under test is what gets applied, not where.
 */
class PlayerStateReconciliationTest {

    private ServerMock server;
    private Scheduler scheduler;
    private PlayerStateStore store;
    private StateReconciler reconciler;
    private PlayerEffects effects;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        scheduler = new InlineScheduler();
        store = new InMemoryPlayerStateStore();
        reconciler = new BukkitStateReconciler(scheduler);
        effects = new BukkitPlayerEffects(scheduler);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void reconcilingAGodSnapshotMakesThePlayerInvulnerable() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerRef ref = BukkitRefs.toRef(alice);

        PlayerStateSnapshot updated = store.update(ref, PlayerStateSnapshot::toggleGod);
        reconciler.reconcile(ref, updated);

        assertThat(alice.isInvulnerable()).isTrue();
    }

    @Test
    void reconcilingAFlySnapshotAllowsFlightAndDisablingLowersThePlayer() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerRef ref = BukkitRefs.toRef(alice);

        reconciler.reconcile(ref, store.update(ref, PlayerStateSnapshot::toggleFly));
        assertThat(alice.getAllowFlight()).isTrue();

        reconciler.reconcile(ref, store.update(ref, PlayerStateSnapshot::toggleFly));
        assertThat(alice.getAllowFlight()).isFalse();
    }

    @Test
    void reconcilingAGameModeSnapshotSetsTheBukkitMode() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerRef ref = BukkitRefs.toRef(alice);

        reconciler.reconcile(ref, store.update(ref, s -> s.withGameMode(GameModeRef.CREATIVE)));

        assertThat(alice.getGameMode()).isEqualTo(GameMode.CREATIVE);
    }

    @Test
    void reconcilingASpeedSnapshotSetsTheWalkAndFlyMultiplier() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerRef ref = BukkitRefs.toRef(alice);

        reconciler.reconcile(ref, store.update(ref, s -> s.withWalkSpeed(SpeedValue.of(5.0))));

        assertThat(alice.getWalkSpeed()).isEqualTo(0.5f);
    }

    @Test
    void healRestoresHealthAndExtinguishes() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.setHealth(4.0);
        alice.setFireTicks(100);

        effects.heal(BukkitRefs.toRef(alice), false);

        assertThat(alice.getHealth()).isEqualTo(alice.getMaxHealth());
        assertThat(alice.getFireTicks()).isZero();
    }

    @Test
    void feedRestoresFood() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.setFoodLevel(2);

        effects.feed(BukkitRefs.toRef(alice));

        assertThat(alice.getFoodLevel()).isEqualTo(20);
    }

    @Test
    void suicideKillsThePlayer() {
        PlayerMock alice = server.addPlayer("Alice");

        effects.kill(BukkitRefs.toRef(alice));

        assertThat(alice.getHealth()).isZero();
    }

    @Test
    void personalTimeSetsAndResetsThePlayerTime() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerRef ref = BukkitRefs.toRef(alice);

        effects.applyTime(ref, PersonalTime.parse("night").orElseThrow());
        assertThat(alice.getPlayerTime()).isEqualTo(14_000L);
    }

    @Test
    void theStoreForgetsAQuitPlayer() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerRef ref = BukkitRefs.toRef(alice);
        store.update(ref, PlayerStateSnapshot::toggleGod);

        store.forget(ref);

        assertThat(store.current(ref).god()).isFalse(); // back to the neutral initial state
    }

    @Test
    void nearbyScanFindsPlayersWithinTheRadiusNearestFirst() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        PlayerMock carol = server.addPlayer("Carol");
        bob.teleport(alice.getLocation().clone().add(5, 0, 0));
        carol.teleport(alice.getLocation().clone().add(50, 0, 0));

        NearbyPlayers nearby = new BukkitNearbyPlayers();
        var found = nearby.within(BukkitRefs.toRef(alice), 100);

        assertThat(found).extracting(n -> n.who().name()).containsExactly("Bob", "Carol");
        assertThat(found).noneMatch(n -> n.who().uuid().equals(alice.getUniqueId())); // self excluded
    }

    @Test
    void anOfflinePlayerReconcilesToNothing() {
        PlayerRef ghost = new PlayerRef(UUID.randomUUID(), "Ghost");

        reconciler.reconcile(ghost, store.update(ghost, PlayerStateSnapshot::toggleGod));

        assertThat(server.getPlayer("Ghost")).isNull(); // no exception, silent no-op
    }

    /** A {@link Scheduler} that runs every task inline so the entity-thread hop is deterministic in tests. */
    private static final class InlineScheduler implements Scheduler {
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
            task.run();
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
}
