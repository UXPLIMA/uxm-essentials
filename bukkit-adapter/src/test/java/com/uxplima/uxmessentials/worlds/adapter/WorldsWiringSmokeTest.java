package com.uxplima.uxmessentials.worlds.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.bootstrap.di.CloseableResources;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RepositoryWorldsPlaceholders;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.WorldsPlaceholders;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.worlds.adapter.inbound.command.WorldCommands;
import com.uxplima.uxmessentials.worlds.adapter.inbound.listener.ForceGamemodeListener;
import com.uxplima.uxmessentials.worlds.adapter.inbound.listener.WorldAccessListener;
import com.uxplima.uxmessentials.worlds.adapter.inbound.listener.WorldPortalListener;
import com.uxplima.uxmessentials.worlds.adapter.outbound.ForcedWorldEntryMarker;
import com.uxplima.uxmessentials.worlds.adapter.outbound.WorldGeneratorResolver;
import com.uxplima.uxmessentials.worlds.application.BackupWorld;
import com.uxplima.uxmessentials.worlds.application.CreateWorld;
import com.uxplima.uxmessentials.worlds.application.DeleteWorld;
import com.uxplima.uxmessentials.worlds.application.ImportWorld;
import com.uxplima.uxmessentials.worlds.application.ListBackups;
import com.uxplima.uxmessentials.worlds.application.ListWorlds;
import com.uxplima.uxmessentials.worlds.application.LoadWorld;
import com.uxplima.uxmessentials.worlds.application.PregenWorld;
import com.uxplima.uxmessentials.worlds.application.ResolvePortalDestination;
import com.uxplima.uxmessentials.worlds.application.RestoreWorld;
import com.uxplima.uxmessentials.worlds.application.SetGamerule;
import com.uxplima.uxmessentials.worlds.application.SetWorldAlias;
import com.uxplima.uxmessentials.worlds.application.SetWorldProperty;
import com.uxplima.uxmessentials.worlds.application.SetWorldSpawn;
import com.uxplima.uxmessentials.worlds.application.UnloadWorld;
import com.uxplima.uxmessentials.worlds.application.UnregisterWorld;
import com.uxplima.uxmessentials.worlds.application.WorldAccessPolicy;
import com.uxplima.uxmessentials.worlds.application.WorldInfo;
import com.uxplima.uxmessentials.worlds.application.WorldNotifier;
import com.uxplima.uxmessentials.worlds.application.WorldTeleportService;
import com.uxplima.uxmessentials.worlds.application.WorldsSettings;
import com.uxplima.uxmessentials.worlds.application.port.GameRuleCatalog;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.BiomeId;
import com.uxplima.uxmessentials.worlds.domain.FlatLayerPlan;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class WorldsWiringSmokeTest {

    @Test
    void settingsReadDefaultsTrueAndOverride() {
        ConfigStore defaults = new ConfigStore() {
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
        };
        WorldsSettings settings = new WorldsSettings(defaults);
        assertThat(settings.protectDefaultWorld()).isTrue();
        assertThat(settings.autoAdoptLoaded()).isTrue();
        assertThat(settings.autoLoadRegistered()).isTrue();

        ConfigStore off = new ConfigStore() {
            @Override
            public boolean getBoolean(String path, boolean fallback) {
                return path.endsWith("protect-default-world") ? false : fallback;
            }

            @Override
            public String getString(String path, String fallback) {
                return fallback;
            }

            @Override
            public int getInt(String path, int fallback) {
                return fallback;
            }
        };
        assertThat(new WorldsSettings(off).protectDefaultWorld()).isFalse();
    }

    @Test
    void wiredCarriesTheForceGamemodeAndAccessListeners() {
        List<Listener> listeners = List.of(
                new ForceGamemodeListener(new NoOpRepository(), new NoOpScheduler()),
                accessListener(),
                portalListener());

        WorldsWiring.Wired wired = new WorldsWiring.Wired(
                List.of(), listeners, () -> {}, () -> {}, resolver(), placeholders(), (player, viewer) -> {});

        assertThat(wired.listeners()).hasAtLeastOneElementOfType(ForceGamemodeListener.class);
        assertThat(wired.listeners()).hasAtLeastOneElementOfType(WorldAccessListener.class);
        assertThat(wired.listeners()).hasAtLeastOneElementOfType(WorldPortalListener.class);
    }

    @Test
    void servicesExposeTheWorldTeleportUseCaseAndStillRegisterCommands() {
        WorldTeleportService worldTeleport = mock(WorldTeleportService.class);
        WorldsServices services = services(worldTeleport);

        assertThat(services.worldTeleport()).isSameAs(worldTeleport);

        List<CommandRegistration> commands = WorldCommands.all(services, mock(Messages.class));
        assertThat(commands).isNotEmpty();
    }

    @Test
    void servicesExposeTheMainOpenerAndTheWorldListOpener() {
        WorldsServices services = services(mock(WorldTeleportService.class));

        // Both the per-world hub and the world picker are opened through seams (the engine menus); invoking them with
        // no-op seams must not throw.
        PlayerRef who = new PlayerRef(java.util.UUID.randomUUID(), "Staff");
        assertThatNoException().isThrownBy(() -> services.openWorldList(mock(org.bukkit.entity.Player.class), who));
        assertThatNoException()
                .isThrownBy(() -> services.openWorldMain(
                        mock(org.bukkit.entity.Player.class),
                        who,
                        com.uxplima.uxmessentials.worlds.domain.WorldName.of("world")));
    }

    @Test
    void servicesExposeThePregenUseCase() {
        WorldsServices services = services(mock(WorldTeleportService.class));

        assertThat(services.pregen()).isNotNull();
    }

    @Test
    void servicesExposeTheBackupAndRestoreUseCases() {
        WorldsServices services = services(mock(WorldTeleportService.class));

        assertThat(services.backupWorld()).isNotNull();
        assertThat(services.listBackups()).isNotNull();
        assertThat(services.restoreWorld()).isNotNull();
    }

    @Test
    void runningTheStopHookDoesNotThrow() {
        WorldsWiring.Wired wired = new WorldsWiring.Wired(
                List.of(), List.of(), () -> {}, () -> {}, resolver(), placeholders(), (player, viewer) -> {});

        assertThatNoException().isThrownBy(() -> wired.stop().run());
    }

    @Test
    void wiredExposesAWorldsPlaceholderSeam() {
        WorldsWiring.Wired wired = new WorldsWiring.Wired(
                List.of(), List.of(), () -> {}, () -> {}, resolver(), placeholders(), (player, viewer) -> {});

        assertThat(wired.worldsPlaceholders()).isNotNull();
    }

    private static WorldAccessListener accessListener() {
        return new WorldAccessListener(
                new NoOpRepository(),
                mock(WorldAccessPolicy.class),
                mock(WorldTeleportService.class),
                mock(WorldEngine.class),
                mock(DomainEventPublisher.class),
                new NoOpScheduler(),
                mock(WorldNotifier.class),
                new ForcedWorldEntryMarker(),
                true);
    }

    private static WorldPortalListener portalListener() {
        return new WorldPortalListener(
                new ResolvePortalDestination(new NoOpRepository()),
                MockBukkit.getMock(),
                java.util.logging.Logger.getLogger("worlds-portal-test"));
    }

    private static WorldsServices services(WorldTeleportService worldTeleport) {
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
                new NoOpScheduler(),
                java.util.Set::of,
                worldTeleport,
                mock(BackupWorld.class),
                mock(ListBackups.class),
                mock(RestoreWorld.class),
                (player, viewer) -> {},
                (player, viewer, world) -> {});
    }

    @Test
    void wiredExposesAGeneratorResolverThatResolvesTheVoidId() {
        WorldsWiring.Wired wired = new WorldsWiring.Wired(
                List.of(), List.of(), () -> {}, () -> {}, resolver(), placeholders(), (player, viewer) -> {});

        assertThat(wired.generatorResolver()).isNotNull();
        assertThat(wired.generatorResolver().resolve("void")).isPresent();
    }

    @Test
    void closeableResourcesRoundTripsTheCapturedResolver() {
        WorldGeneratorResolver resolver = resolver();
        CloseableResources resources = new CloseableResources();

        assertThat(resources.worldGeneratorResolver()).isNull(); // null until worlds wires
        resources.worldGeneratorResolver(resolver);

        assertThat(resources.worldGeneratorResolver()).isSameAs(resolver);
    }

    // The void/flat generators resolve their biome through Registry.BIOME and their layers through
    // Material.matchMaterial, so a running (mock) server is required to build the resolver.
    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    private static WorldGeneratorResolver resolver() {
        return new WorldGeneratorResolver(
                FlatLayerPlan.defaults(), BiomeId.of("plains"), BiomeId.of("plains"), new NoOpLogger());
    }

    private static WorldsPlaceholders placeholders() {
        return new RepositoryWorldsPlaceholders(new NoOpRepository(), mock(WorldEngine.class));
    }

    private static final class NoOpLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
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

    private static final class NoOpScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {}

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void onEntity(PlayerRef player, Runnable task) {}

        @Override
        public void async(Runnable task) {}

        @Override
        public void asyncAfter(Duration delay, Runnable task) {}
    }
}
