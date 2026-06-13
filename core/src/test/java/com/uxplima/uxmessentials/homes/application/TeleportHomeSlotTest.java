package com.uxplima.uxmessentials.homes.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The {@code /home <slot>} teleport use case under the slot model: an occupied slot hands that home to the
 * teleporter and reports {@code HOME_TELEPORTING}, while an empty slot is rejected with
 * {@link HomeError#NOT_FOUND} and no hop happens. A capturing teleporter records which {@link Home} the use
 * case picked.
 */
class TeleportHomeSlotTest {

    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeHomeRepository repository;
    private CapturingTeleporter teleporter;
    private TeleportHome teleportHome;

    @BeforeEach
    void setUp() {
        repository = new FakeHomeRepository();
        teleporter = new CapturingTeleporter();
        teleportHome = new TeleportHome(repository, teleporter, silentNotifier());
    }

    @Test
    void anOccupiedSlotIsTeleportedTo() {
        repository.save(Home.create(WHO, HomeSlot.of(2), Position.of(WORLD, 7, 64, 8), Instant.EPOCH));

        Result<Unit, HomeError> result = teleportHome.toSlot(WHO, HomeSlot.of(2));

        assertThat(result.isOk()).isTrue();
        assertThat(teleporter.lastSlot()).contains(HomeSlot.of(2));
    }

    @Test
    void anEmptySlotIsRejectedWithNotFoundAndNoHop() {
        Result<Unit, HomeError> result = teleportHome.toSlot(WHO, HomeSlot.of(0));

        assertThat(result.errorOrThrow()).isEqualTo(HomeError.NOT_FOUND);
        assertThat(teleporter.lastSlot()).isEmpty();
    }

    private static HomeNotifier silentNotifier() {
        Messages messages = (viewer, key, placeholders) -> key.key();
        MessageSink sink = (viewer, renderedText) -> {};
        return new HomeNotifier(messages, sink);
    }

    /** An in-memory {@link HomeRepository} keyed by slot for the single owner under test. */
    private static final class FakeHomeRepository implements HomeRepository {
        private final Map<HomeSlot, Home> homes = new TreeMap<>(java.util.Comparator.comparingInt(HomeSlot::index));

        @Override
        public HomeSet load(PlayerRef owner) {
            return HomeSet.of(owner, List.copyOf(homes.values()));
        }

        @Override
        public int count(PlayerRef owner) {
            return homes.size();
        }

        @Override
        public Optional<Home> findSlot(PlayerRef owner, HomeSlot slot) {
            return Optional.ofNullable(homes.get(slot));
        }

        @Override
        public void save(Home home) {
            homes.put(home.slot(), home);
        }

        @Override
        public void deleteSlot(PlayerRef owner, HomeSlot slot) {
            homes.remove(slot);
        }
    }

    /** A {@link HomeTeleporter} that records the last home it was asked to send the player to. */
    private static final class CapturingTeleporter implements HomeTeleporter {
        private @Nullable Home lastHome;

        @Override
        public void teleportTo(PlayerRef who, Home home) {
            lastHome = home;
        }

        Optional<HomeSlot> lastSlot() {
            return Optional.ofNullable(lastHome).map(Home::slot);
        }
    }
}
