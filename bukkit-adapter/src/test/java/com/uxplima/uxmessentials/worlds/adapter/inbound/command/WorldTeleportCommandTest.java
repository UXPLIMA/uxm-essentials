package com.uxplima.uxmessentials.worlds.adapter.inbound.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.worlds.adapter.WorldsServices;
import com.uxplima.uxmessentials.worlds.adapter.inbound.gui.WorldMainView;
import com.uxplima.uxmessentials.worlds.application.BackupWorld;
import com.uxplima.uxmessentials.worlds.application.CreateWorld;
import com.uxplima.uxmessentials.worlds.application.DeleteWorld;
import com.uxplima.uxmessentials.worlds.application.ImportWorld;
import com.uxplima.uxmessentials.worlds.application.ListBackups;
import com.uxplima.uxmessentials.worlds.application.ListWorlds;
import com.uxplima.uxmessentials.worlds.application.LoadWorld;
import com.uxplima.uxmessentials.worlds.application.PregenWorld;
import com.uxplima.uxmessentials.worlds.application.RestoreWorld;
import com.uxplima.uxmessentials.worlds.application.SetGamerule;
import com.uxplima.uxmessentials.worlds.application.SetWorldAlias;
import com.uxplima.uxmessentials.worlds.application.SetWorldProperty;
import com.uxplima.uxmessentials.worlds.application.SetWorldSpawn;
import com.uxplima.uxmessentials.worlds.application.UnloadWorld;
import com.uxplima.uxmessentials.worlds.application.UnregisterWorld;
import com.uxplima.uxmessentials.worlds.application.WorldInfo;
import com.uxplima.uxmessentials.worlds.application.WorldTeleportService;
import com.uxplima.uxmessentials.worlds.application.port.GameRuleCatalog;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /worlds spawn} and {@code /worlds tp} verbs through their real Brigadier nodes:
 * {@code spawn} (current and named) routes into {@link WorldTeleportService#spawn}, while {@code tp} (self and
 * targeted) routes into {@link WorldTeleportService#forced}. The synchronous scheduler fires the global-thread
 * bridge inline so the routed call is observable.
 */
class WorldTeleportCommandTest {

    private ServerMock server;
    private PlayerMock alice;
    private WorldTeleportService teleport;
    private WorldsServices services;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        alice.setOp(true);
        teleport = mock(WorldTeleportService.class);
        when(teleport.spawn(any(), any())).thenReturn(Result.ok());
        when(teleport.forced(any(), any(), any())).thenReturn(Result.ok());
        services = services(teleport);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void spawnCurrentRoutesIntoTheSendersWorldSpawn() {
        execute("worlds spawn");

        verify(teleport).spawn(any(PlayerRef.class), eq(WorldName.of("world")));
    }

    @Test
    void spawnNamedRoutesIntoTheNamedWorldSpawn() {
        execute("worlds spawn nether");

        verify(teleport).spawn(any(PlayerRef.class), eq(WorldName.of("nether")));
    }

    @Test
    void tpSelfForcesTheSenderToTheNamedWorld() {
        execute("worlds tp nether");

        verify(teleport).forced(any(PlayerRef.class), any(PlayerRef.class), eq(WorldName.of("nether")));
    }

    @Test
    void tpOtherForcesTheNamedTargetToTheNamedWorld() {
        PlayerMock bob = server.addPlayer("Bob");

        execute("worlds tp nether Bob");

        verify(teleport).forced(refFor(alice), refFor(bob), eq(WorldName.of("nether")));
    }

    private static PlayerRef refFor(PlayerMock player) {
        return eq(com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs.toRef(player));
    }

    private void execute(String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new WorldCommand(services, mock(Messages.class)).build());
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(alice));
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private static WorldsServices services(WorldTeleportService teleport) {
        return new WorldsServices(
                mock(CreateWorld.class),
                mock(ImportWorld.class),
                mock(LoadWorld.class),
                mock(UnloadWorld.class),
                mock(UnregisterWorld.class),
                mock(DeleteWorld.class),
                mock(ListWorlds.class),
                mock(WorldInfo.class),
                mock(SetWorldProperty.class),
                mock(SetGamerule.class),
                mock(SetWorldSpawn.class),
                mock(SetWorldAlias.class),
                mock(PregenWorld.class),
                mock(GameRuleCatalog.class),
                new NoOpRepository(),
                new SyncScheduler(),
                Set::of,
                teleport,
                mock(BackupWorld.class),
                mock(ListBackups.class),
                mock(RestoreWorld.class),
                (player, viewer) -> {},
                mock(WorldMainView.class));
    }

    private static final class NoOpRepository implements WorldRepository {
        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return Optional.empty();
        }

        @Override
        public List<ManagedWorld> all() {
            return List.of();
        }

        @Override
        public boolean exists(WorldName name) {
            return false;
        }

        @Override
        public void save(ManagedWorld world) {}

        @Override
        public void delete(WorldName name) {}
    }

    private static final class SyncScheduler implements Scheduler {
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
