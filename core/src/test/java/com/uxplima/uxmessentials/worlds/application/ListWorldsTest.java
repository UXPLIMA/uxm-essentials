package com.uxplima.uxmessentials.worlds.application;

import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;

/** {@code /worlds list} over the world table every backend of a network shares. */
class ListWorldsTest {

    private static final WorldName SPAWN = WorldName.of("spawn");
    private static final WorldName GAMES = WorldName.of("games");

    private final InMemoryWorlds.Table table = new InMemoryWorlds.Table();
    private final FakeWorldEngine engine = new FakeWorldEngine();

    private static ManagedWorld registered(WorldName name) {
        return ManagedWorld.created(name, WorldSpec.normal(), true, Optional.empty(), NOW);
    }

    @Test
    void anUnclaimedWorldSomeServerAlreadyCopiedIsLeftOutAndOneNobodyHasIsListedAsUnclaimed() {
        table.put(NetworkWorld.UNSCOPED, registered(SPAWN));
        table.put(NetworkWorld.UNSCOPED, registered(GAMES));
        table.put("survival", registered(SPAWN));

        var entries = new ListWorlds(new InMemoryWorlds(table, "lobby"), engine, "lobby", true).all();

        assertThat(entries)
                .extracting(entry -> entry.name().value() + "@" + entry.server().orElse("unclaimed"))
                .containsExactly("spawn@survival", "games@unclaimed");
    }

    @Test
    void aStandaloneServerListsItsWorldsUnderTheServerItKeepsThemUnder() {
        table.put(NetworkWorld.UNSCOPED, registered(SPAWN));

        var entries = new ListWorlds(
                        new InMemoryWorlds(table, NetworkWorld.UNSCOPED), engine, NetworkWorld.UNSCOPED, false)
                .all();

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.here()).isTrue();
            assertThat(entry.server()).contains(NetworkWorld.UNSCOPED);
        });
    }
}
