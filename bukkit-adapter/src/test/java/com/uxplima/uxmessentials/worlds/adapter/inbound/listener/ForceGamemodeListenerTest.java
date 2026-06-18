package com.uxplima.uxmessentials.worlds.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.event.player.PlayerJoinEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ForcedGameMode;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the per-world force-gamemode enforcement: a player joining a world whose
 * {@code force-gamemode} is {@code ADVENTURE} (and who lacks the bypass node) is switched to adventure, while a
 * world forcing {@code NONE} or a player holding {@code uxmessentials.world.gamemode.bypass} is left alone. The
 * {@link Scheduler} is an inline fake so the entity-thread hop runs deterministically and the repository is a
 * tiny in-memory stub returning the configured {@link ManagedWorld}.
 */
class ForceGamemodeListenerTest {

    private ServerMock server;
    private World world;
    private FakeWorldRepository repository;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("w");
        repository = new FakeWorldRepository();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void forcesGamemodeOnJoinWhenSetAndNoBypass() {
        repository.put(managed("w", ForcedGameMode.ADVENTURE));
        ForceGamemodeListener listener = new ForceGamemodeListener(repository, new InlineScheduler());

        PlayerMock alice = server.addPlayer("Alice");
        alice.setGameMode(GameMode.SURVIVAL);
        listener.onJoin(new PlayerJoinEvent(alice, Component.empty()));

        assertThat(alice.getGameMode()).isEqualTo(GameMode.ADVENTURE);
    }

    @Test
    void leavesGamemodeWhenForceIsNone() {
        repository.put(managed("w", ForcedGameMode.NONE));
        ForceGamemodeListener listener = new ForceGamemodeListener(repository, new InlineScheduler());

        PlayerMock bob = server.addPlayer("Bob");
        bob.setGameMode(GameMode.SURVIVAL);
        listener.onJoin(new PlayerJoinEvent(bob, Component.empty()));

        assertThat(bob.getGameMode()).isEqualTo(GameMode.SURVIVAL);
    }

    @Test
    void leavesGamemodeWhenPlayerHasBypass() {
        repository.put(managed("w", ForcedGameMode.ADVENTURE));
        ForceGamemodeListener listener = new ForceGamemodeListener(repository, new InlineScheduler());

        PlayerMock staff = server.addPlayer("Staff");
        staff.setGameMode(GameMode.SURVIVAL);
        staff.addAttachment(MockBukkit.createMockPlugin(), ForceGamemodeListener.BYPASS_NODE, true);
        listener.onJoin(new PlayerJoinEvent(staff, Component.empty()));

        assertThat(staff.getGameMode()).isEqualTo(GameMode.SURVIVAL);
    }

    private ManagedWorld managed(String name, ForcedGameMode forced) {
        WorldName worldName = WorldName.of(name);
        WorldSpec spec = WorldSpec.normal();
        WorldSettings settings = WorldSettings.defaults().with(WorldProperties.FORCE_GAMEMODE, forced);
        return new ManagedWorld(
                worldName,
                spec,
                Optional.empty(),
                true,
                true,
                Optional.of(world.getUID()),
                Instant.EPOCH,
                Optional.empty(),
                settings);
    }

    /** An in-memory {@link WorldRepository} returning only the worlds explicitly seeded by a test. */
    private static final class FakeWorldRepository implements WorldRepository {
        private final Map<WorldName, ManagedWorld> byName = new HashMap<>();

        void put(ManagedWorld world) {
            byName.put(world.name(), world);
        }

        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return Optional.ofNullable(byName.get(name));
        }

        @Override
        public List<ManagedWorld> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(WorldName name) {
            return byName.containsKey(name);
        }

        @Override
        public void save(ManagedWorld world) {
            byName.put(world.name(), world);
        }

        @Override
        public void delete(WorldName name) {
            byName.remove(name);
        }
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
