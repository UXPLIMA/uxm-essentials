package com.uxplima.uxmessentials.worlds.application;

import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;

/** The enable-time reconcile of one backend, on the world table every backend of a network shares. */
class ReconcileAcrossServersTest {

    private static final String LOBBY = "lobby";
    private static final String SURVIVAL = "survival";
    private static final WorldName WORLD = WorldName.of("world");
    private static final WorldName ARENA = WorldName.of("arena");

    private final InMemoryWorlds.Table table = new InMemoryWorlds.Table();
    private final InMemoryWorlds lobbyWorlds = new InMemoryWorlds(table, LOBBY);
    private final FakeWorldEngine lobbyEngine = new FakeWorldEngine();

    private void reconcileLobby() {
        new ReconcileWorldsOnEnable(lobbyWorlds, lobbyEngine, new RecordingEvents(), CLOCK, () -> true, () -> true)
                .run();
    }

    /** A networked backend under the shipped name: it reads the unclaimed rows and may not create their worlds. */
    private void reconcileUnnamed(InMemoryWorlds unnamed) {
        new ReconcileWorldsOnEnable(
                        unnamed, lobbyEngine, new RecordingEvents(), CLOCK, () -> true, () -> true, () -> false)
                .run();
    }

    private static ManagedWorld registered(WorldName name) {
        return ManagedWorld.created(name, WorldSpec.normal(), true, Optional.empty(), NOW);
    }

    @Test
    void aWorldRegisteredOnAnotherServerIsNeitherLoadedNorCreatedHere() {
        table.put(SURVIVAL, registered(ARENA));

        reconcileLobby();

        assertThat(lobbyEngine.loads()).doesNotContain(ARENA);
        assertThat(lobbyWorlds.find(ARENA)).isEmpty();
    }

    @Test
    void thisServersOwnRegisteredWorldIsStillLoaded() {
        lobbyWorlds.save(registered(ARENA));
        lobbyEngine.onDisk(ARENA, false, Position.of(new WorldRef(new UUID(0, 7), "arena"), 0, 64, 0));

        reconcileLobby();

        assertThat(lobbyEngine.loads()).containsExactly(ARENA);
    }

    @Test
    void aServerWithoutANameOfItsOwnLoadsUnclaimedWorldsItHasButCreatesNoneWithoutAFolder() {
        table.put(NetworkWorld.UNSCOPED, registered(ARENA));
        table.put(NetworkWorld.UNSCOPED, registered(WorldName.of("games")));
        lobbyEngine.onDisk(ARENA, false, Position.of(new WorldRef(new UUID(0, 7), "arena"), 0, 64, 0));

        reconcileUnnamed(new InMemoryWorlds(table, NetworkWorld.UNSCOPED));

        assertThat(lobbyEngine.loads()).containsExactly(ARENA);
    }

    @Test
    void twoServersRunningAWorldOfTheSameNameKeepTheirOwnRowsAndSettings() {
        ManagedWorld survivalWorld = registered(WORLD);
        table.put(SURVIVAL, survivalWorld.withSettings(survivalWorld.settings().with(WorldProperties.PVP, false)));

        reconcileLobby();
        ManagedWorld adopted = lobbyWorlds.find(WORLD).orElseThrow();
        lobbyWorlds.save(adopted.withSettings(adopted.settings().with(WorldProperties.PLAYER_LIMIT, 5)));

        ManagedWorld survivals = table.find(SURVIVAL, WORLD).orElseThrow();
        assertThat(survivals.settings().get(WorldProperties.PVP)).isFalse();
        assertThat(survivals.settings().get(WorldProperties.PLAYER_LIMIT)).isZero();
        assertThat(lobbyWorlds.find(WORLD).orElseThrow().settings().get(WorldProperties.PLAYER_LIMIT))
                .isEqualTo(5);
        assertThat(lobbyWorlds.network()).hasSize(2);
    }
}
