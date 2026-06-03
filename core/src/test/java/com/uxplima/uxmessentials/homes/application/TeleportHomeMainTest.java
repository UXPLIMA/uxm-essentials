package com.uxplima.uxmessentials.homes.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.application.port.MainHomePreference;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * How plain {@code /home} (no name) resolves once the main-home choice exists. A stored choice wins; a
 * stored choice that no longer names a live home (deleted or renamed) is dropped and the first-created
 * home is used; with no choice the first-created home is used; an empty set is rejected with
 * {@link HomeError#NONE_SET}. A capturing teleporter records which {@link Home} the use case picked.
 */
class TeleportHomeMainTest {

    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeHomeRepository repository;
    private FakeMainHomePreference preferences;
    private CapturingTeleporter teleporter;
    private TeleportHome teleportHome;

    @BeforeEach
    void setUp() {
        repository = new FakeHomeRepository();
        preferences = new FakeMainHomePreference();
        teleporter = new CapturingTeleporter();
        teleportHome = new TeleportHome(repository, preferences, teleporter, silentNotifier());
    }

    @Test
    void aStoredMainHomeWinsOverTheFirstCreated() {
        repository.save(homeAt("first", Instant.ofEpochMilli(1_000)));
        repository.save(homeAt("camp", Instant.ofEpochMilli(2_000)));
        preferences.setMainHome(WHO, HomeName.of("camp"));

        teleportHome.toDefault(WHO);

        assertThat(teleporter.lastName()).contains(HomeName.of("camp"));
    }

    @Test
    void aStaleMainHomeFallsBackToTheFirstCreated() {
        repository.save(homeAt("first", Instant.ofEpochMilli(1_000)));
        repository.save(homeAt("camp", Instant.ofEpochMilli(2_000)));
        preferences.setMainHome(WHO, HomeName.of("camp"));
        repository.delete(WHO, HomeName.of("camp")); // the chosen main home is gone

        teleportHome.toDefault(WHO);

        assertThat(teleporter.lastName()).contains(HomeName.of("first"));
    }

    @Test
    void noStoredMainHomeUsesTheFirstCreated() {
        repository.save(homeAt("first", Instant.ofEpochMilli(1_000)));
        repository.save(homeAt("camp", Instant.ofEpochMilli(2_000)));

        teleportHome.toDefault(WHO);

        assertThat(teleporter.lastName()).contains(HomeName.of("first"));
    }

    @Test
    void anEmptySetIsRejectedWithNoneSet() {
        var result = teleportHome.toDefault(WHO);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(HomeError.NONE_SET);
        assertThat(teleporter.lastName()).isEmpty();
    }

    private Home homeAt(String name, Instant createdAt) {
        return new Home(WHO, HomeName.of(name), Position.of(WORLD, 0, 64, 0), createdAt);
    }

    private static HomeNotifier silentNotifier() {
        Messages messages = (viewer, key, placeholders) -> key.key();
        MessageSink sink = (viewer, renderedText) -> {};
        return new HomeNotifier(messages, sink);
    }

    /** An in-memory {@link HomeRepository} preserving insertion order so the first-created default is stable. */
    private static final class FakeHomeRepository implements HomeRepository {
        private final Map<HomeName, Home> homes = new java.util.LinkedHashMap<>();

        @Override
        public HomeSet load(PlayerRef owner) {
            return HomeSet.of(owner, List.copyOf(homes.values()));
        }

        @Override
        public int count(PlayerRef owner) {
            return homes.size();
        }

        @Override
        public void save(Home home) {
            homes.put(home.name(), home);
        }

        @Override
        public void rename(PlayerRef owner, HomeName from, HomeName to) {
            Home moved = homes.remove(from);
            if (moved != null) {
                homes.put(to, moved.renamedTo(to));
            }
        }

        @Override
        public void delete(PlayerRef owner, HomeName name) {
            homes.remove(name);
        }
    }

    /** An in-memory {@link MainHomePreference} for one owner. */
    private static final class FakeMainHomePreference implements MainHomePreference {
        private final Map<UUID, HomeName> choices = new HashMap<>();

        @Override
        public Optional<HomeName> mainHomeOf(PlayerRef owner) {
            return Optional.ofNullable(choices.get(owner.uuid()));
        }

        @Override
        public void setMainHome(PlayerRef owner, HomeName name) {
            choices.put(owner.uuid(), name);
        }

        @Override
        public void clear(PlayerRef owner) {
            choices.remove(owner.uuid());
        }
    }

    /** A {@link HomeTeleporter} that records the last home it was asked to send the player to. */
    private static final class CapturingTeleporter implements HomeTeleporter {
        private @Nullable Home lastHome;

        @Override
        public void teleportTo(PlayerRef who, Home home) {
            lastHome = home;
        }

        Optional<HomeName> lastName() {
            return Optional.ofNullable(lastHome).map(Home::name);
        }
    }
}
