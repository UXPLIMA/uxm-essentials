package com.uxplima.uxmessentials.homes.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /delhome} behaviour, focused on the invite cascade: deleting a home removes the row and clears every
 * invite granted against that slot, so no guest row outlives the home. An empty slot is still rejected with
 * {@link HomeError#NOT_FOUND} and touches no invites.
 */
class DeleteHomeTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Owner");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final HomeSlot SLOT = HomeSlot.of(0);

    private FakeHomeRepository repository;
    private FakeInviteRepository invites;
    private DeleteHome deleteHome;

    @BeforeEach
    void setUp() {
        repository = new FakeHomeRepository();
        invites = new FakeInviteRepository();
        deleteHome = new DeleteHome(repository, invites, silentNotifier(), new RecordingPublisher());
    }

    @Test
    void deletingAHomeClearsItsInvites() {
        repository.save(Home.create(OWNER, SLOT, Position.of(WORLD, 1, 64, 1), Instant.EPOCH));
        invites.addInvite(OWNER, SLOT, UUID.randomUUID());

        Result<Unit, HomeError> result = deleteHome.delete(OWNER, SLOT);

        assertThat(result.isOk()).isTrue();
        assertThat(invites.removeAllCalls).containsExactly(SLOT);
        assertThat(invites.invites(OWNER, SLOT)).isEmpty();
    }

    @Test
    void deletingAnEmptySlotIsRejectedAndTouchesNoInvites() {
        Result<Unit, HomeError> result = deleteHome.delete(OWNER, SLOT);

        assertThat(result.errorOrThrow()).isEqualTo(HomeError.NOT_FOUND);
        assertThat(invites.removeAllCalls).isEmpty();
    }

    private static HomeNotifier silentNotifier() {
        Messages messages = (viewer, key, placeholders) -> key.key();
        MessageSink sink = (viewer, renderedText) -> {};
        return new HomeNotifier(messages, sink);
    }

    /** In-memory per-owner repository keyed by (owner, slot). */
    private static final class FakeHomeRepository implements HomeRepository {
        private final Map<UUID, Map<HomeSlot, Home>> store = new HashMap<>();

        private Map<HomeSlot, Home> slots(PlayerRef owner) {
            return store.computeIfAbsent(
                    owner.uuid(), k -> new TreeMap<>(java.util.Comparator.comparingInt(HomeSlot::index)));
        }

        @Override
        public HomeSet load(PlayerRef owner) {
            return HomeSet.of(owner, List.copyOf(slots(owner).values()));
        }

        @Override
        public int count(PlayerRef owner) {
            return slots(owner).size();
        }

        @Override
        public Optional<Home> findSlot(PlayerRef owner, HomeSlot slot) {
            return Optional.ofNullable(slots(owner).get(slot));
        }

        @Override
        public void save(Home home) {
            slots(home.owner()).put(home.slot(), home);
        }

        @Override
        public void deleteSlot(PlayerRef owner, HomeSlot slot) {
            slots(owner).remove(slot);
        }

        @Override
        public void deleteAll(PlayerRef owner) {
            slots(owner).clear();
        }
    }

    /** In-memory invite store that records every {@code removeAll} so the cascade can be asserted. */
    private static final class FakeInviteRepository implements HomeInviteRepository {
        private final Map<UUID, Map<HomeSlot, Set<UUID>>> store = new HashMap<>();
        private final List<HomeSlot> removeAllCalls = new ArrayList<>();

        private Set<UUID> guests(PlayerRef owner, HomeSlot slot) {
            return store.computeIfAbsent(owner.uuid(), k -> new HashMap<>())
                    .computeIfAbsent(slot, k -> new HashSet<>());
        }

        @Override
        public Set<UUID> invites(PlayerRef owner, HomeSlot slot) {
            return Set.copyOf(guests(owner, slot));
        }

        @Override
        public void addInvite(PlayerRef owner, HomeSlot slot, UUID invited) {
            guests(owner, slot).add(invited);
        }

        @Override
        public void removeInvite(PlayerRef owner, HomeSlot slot, UUID invited) {
            guests(owner, slot).remove(invited);
        }

        @Override
        public void removeAll(PlayerRef owner, HomeSlot slot) {
            removeAllCalls.add(slot);
            Map<HomeSlot, Set<UUID>> bySlot = store.get(owner.uuid());
            if (bySlot != null) {
                bySlot.remove(slot);
            }
        }

        @Override
        public void removeAllForOwner(PlayerRef owner) {
            store.remove(owner.uuid());
        }
    }

    private static final class RecordingPublisher implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {
            // no-op; this test asserts the invite cascade, not the event bus
        }
    }
}
