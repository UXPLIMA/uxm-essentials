package com.uxplima.uxmessentials.homes.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/** A respawn at home stays on the server the player respawns on. */
class RepositoryHomeRespawnLocatorTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000aaaa"), "world");
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");

    private static Home home(int slot, double x, String server) {
        return Home.create(ALEX, HomeSlot.of(slot), Position.of(WORLD, x, 64, 0), server, NOW);
    }

    private static HomeRepository cached(List<Home> homes) {
        return new HomeRepository() {
            @Override
            public HomeSet load(PlayerRef owner) {
                return HomeSet.of(owner, homes);
            }

            @Override
            public int count(PlayerRef owner) {
                return homes.size();
            }

            @Override
            public Optional<Home> findSlot(PlayerRef owner, HomeSlot slot) {
                return homes.stream().filter(home -> home.slot().equals(slot)).findFirst();
            }

            @Override
            public void save(Home home) {}

            @Override
            public void deleteSlot(PlayerRef owner, HomeSlot slot) {}

            @Override
            public void deleteAll(PlayerRef owner) {}

            @Override
            public Optional<List<Home>> peek(PlayerRef owner) {
                return Optional.of(homes);
            }
        };
    }

    @Test
    void aLowerSlotHomeOnAnotherServerIsSkippedForTheLowestHomeHere() {
        HomeRepository repository = cached(List.of(home(0, 1, "survival"), home(1, 2, "lobby"), home(2, 3, "lobby")));

        Optional<Position> respawn =
                new RepositoryHomeRespawnLocator(repository, home -> home.livesElsewhere("lobby")).respawnHome(ALEX);

        assertThat(respawn.map(Position::x)).contains(2.0);
    }

    @Test
    void withEveryHomeOnAnotherServerThereIsNoRespawnAtHome() {
        HomeRepository repository = cached(List.of(home(0, 1, "survival")));

        assertThat(new RepositoryHomeRespawnLocator(repository, home -> home.livesElsewhere("lobby")).respawnHome(ALEX))
                .isEmpty();
    }

    @Test
    void withHomesKeptLocalTheLowestSlotWinsWhateverItsServer() {
        HomeRepository repository = cached(List.of(home(1, 2, "lobby"), home(0, 1, "survival")));

        assertThat(new RepositoryHomeRespawnLocator(repository, home -> false)
                        .respawnHome(ALEX)
                        .map(Position::x))
                .contains(1.0);
    }
}
