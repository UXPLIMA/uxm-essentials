package com.uxplima.uxmessentials.worlds.adapter.inbound.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
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
import com.uxplima.uxmessentials.worlds.domain.BackupId;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /worlds backup|backups|restore|restoreconfirm} verbs through their real
 * Brigadier nodes: each routes to the matching backup use case, and {@code restore} parses its trailing token
 * into the {@link BackupId} handed to {@code request}.
 */
class WorldBackupCommandTest {

    private ServerMock server;
    private PlayerMock alice;
    private BackupWorld backupWorld;
    private ListBackups listBackups;
    private RestoreWorld restoreWorld;
    private Messages messages;
    private WorldsServices services;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        alice = server.addPlayer("Alice");
        alice.setOp(true);
        rb();
        messages = mock(Messages.class);
        when(messages.resolve(any(), any(), any())).thenReturn("");
        services = services(new SingleWorldRepository(WorldName.of("world")));
    }

    private void rb() {
        backupWorld = mock(BackupWorld.class);
        listBackups = mock(ListBackups.class);
        restoreWorld = mock(RestoreWorld.class);
        when(listBackups.list(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void backupRoutesToTheBackupUseCase() {
        execute("worlds backup world");

        verify(backupWorld).backup(any(PlayerRef.class), eq(WorldName.of("world")));
    }

    @Test
    void backupsRoutesToTheListUseCase() {
        execute("worlds backups world");

        verify(listBackups).list(eq(WorldName.of("world")));
    }

    @Test
    void restoreRoutesToRequestWithTheParsedBackupId() {
        execute("worlds restore world 20240101-120000");

        verify(restoreWorld)
                .request(any(PlayerRef.class), eq(WorldName.of("world")), eq(BackupId.of("20240101-120000")));
    }

    @Test
    void restoreConfirmRoutesToConfirm() {
        execute("worlds restoreconfirm world");

        verify(restoreWorld).confirm(any(PlayerRef.class), eq(WorldName.of("world")));
        verify(restoreWorld, never()).request(any(), any(), any());
    }

    private void execute(String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new WorldCommand(services, messages).build());
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(alice));
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private WorldsServices services(WorldRepository repository) {
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
                repository,
                new SyncScheduler(),
                Set::of,
                mock(WorldTeleportService.class),
                backupWorld,
                listBackups,
                restoreWorld,
                (player, viewer) -> {},
                mock(WorldMainView.class));
    }

    /** A repository that recognises exactly one managed world, so the name argument suggests and resolves it. */
    private static final class SingleWorldRepository implements WorldRepository {
        private final ManagedWorld world;

        SingleWorldRepository(WorldName name) {
            WorldSpec spec = new WorldSpec(
                    WorldEnvironment.NORMAL,
                    WorldGenType.NORMAL,
                    Optional.empty(),
                    Optional.empty(),
                    true,
                    Optional.empty());
            this.world = ManagedWorld.created(name, spec, true, Optional.empty(), Instant.EPOCH);
        }

        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return world.name().equals(name) ? Optional.of(world) : Optional.empty();
        }

        @Override
        public List<ManagedWorld> all() {
            return List.of(world);
        }

        @Override
        public boolean exists(WorldName name) {
            return world.name().equals(name);
        }

        @Override
        public void save(ManagedWorld saved) {}

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
