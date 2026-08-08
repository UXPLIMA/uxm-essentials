package com.uxplima.uxmessentials.homes.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import com.uxplima.uxmessentials.homes.domain.event.HomeVisibilityChanged;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The visibility flip and invite/uninvite use cases over in-memory fakes: happy paths flip the home, record
 * the guest, and render the right feedback; a missing slot is rejected with {@link HomeError#NOT_FOUND}.
 */
class HomeVisibilityAndInvitesTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Owner");
    private static final PlayerRef GUEST = new PlayerRef(UUID.randomUUID(), "Guest");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final HomeSlot SLOT = HomeSlot.of(0);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-13T10:00:00Z"), ZoneOffset.UTC);

    private FakeHomeRepository repository;
    private FakeInviteRepository invites;
    private CapturingNotifier notifier;
    private RecordingPublisher events;

    @BeforeEach
    void setUp() {
        repository = new FakeHomeRepository();
        invites = new FakeInviteRepository();
        notifier = new CapturingNotifier();
        events = new RecordingPublisher();
    }

    // --- SetHomeVisibility ---

    @Test
    void setVisibilityPublicFlipsTheHomeAndPublishesEvent() {
        repository.save(home());
        SetHomeVisibility useCase = new SetHomeVisibility(repository, notifier.notifier(), events, CLOCK);

        Result<Unit, HomeError> result = useCase.setVisibility(OWNER, SLOT, true);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.findSlot(OWNER, SLOT).orElseThrow().isPublic()).isTrue();
        assertThat(notifier.lastKey).isEqualTo(HomesMessageKey.HOME_VISIBILITY_PUBLIC);
        assertThat(events.published).hasSize(1).first().isInstanceOf(HomeVisibilityChanged.class);
    }

    @Test
    void setVisibilityPrivateRendersThePrivateFeedback() {
        repository.save(home().withVisibility(true, CLOCK.instant()));
        SetHomeVisibility useCase = new SetHomeVisibility(repository, notifier.notifier(), events, CLOCK);

        Result<Unit, HomeError> result = useCase.setVisibility(OWNER, SLOT, false);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.findSlot(OWNER, SLOT).orElseThrow().isPublic()).isFalse();
        assertThat(notifier.lastKey).isEqualTo(HomesMessageKey.HOME_VISIBILITY_PRIVATE);
    }

    @Test
    void setVisibilityOnMissingSlotIsRejectedWithNotFound() {
        SetHomeVisibility useCase = new SetHomeVisibility(repository, notifier.notifier(), events, CLOCK);

        Result<Unit, HomeError> result = useCase.setVisibility(OWNER, SLOT, true);

        assertThat(result.errorOrThrow()).isEqualTo(HomeError.NOT_FOUND);
        assertThat(events.published).isEmpty();
    }

    // --- InviteToHome ---

    @Test
    void inviteRecordsTheGuestAndNotifies() {
        repository.save(home());
        InviteToHome useCase = new InviteToHome(repository, invites, notifier.notifier());

        Result<Unit, HomeError> result = useCase.invite(OWNER, SLOT, GUEST);

        assertThat(result.isOk()).isTrue();
        assertThat(invites.invites(OWNER, SLOT)).contains(GUEST.uuid());
        assertThat(notifier.lastKey).isEqualTo(HomesMessageKey.HOME_INVITED);
        assertThat(notifier.lastPlaceholders).containsEntry("player", GUEST.name());
    }

    @Test
    void inviteOnMissingSlotIsRejectedWithNotFound() {
        InviteToHome useCase = new InviteToHome(repository, invites, notifier.notifier());

        Result<Unit, HomeError> result = useCase.invite(OWNER, SLOT, GUEST);

        assertThat(result.errorOrThrow()).isEqualTo(HomeError.NOT_FOUND);
        assertThat(invites.invites(OWNER, SLOT)).isEmpty();
    }

    // --- UninviteFromHome ---

    @Test
    void uninviteRevokesTheGuestAndNotifies() {
        invites.addInvite(OWNER, SLOT, GUEST.uuid());
        UninviteFromHome useCase = new UninviteFromHome(invites, notifier.notifier());

        useCase.uninvite(OWNER, SLOT, GUEST);

        assertThat(invites.invites(OWNER, SLOT)).doesNotContain(GUEST.uuid());
        assertThat(notifier.lastKey).isEqualTo(HomesMessageKey.HOME_UNINVITED);
        assertThat(notifier.lastPlaceholders).containsEntry("player", GUEST.name());
    }

    @Test
    void uninviteAGuestThatWasNeverInvitedStillReportsTheRevoke() {
        UninviteFromHome useCase = new UninviteFromHome(invites, notifier.notifier());

        useCase.uninvite(OWNER, SLOT, GUEST);

        assertThat(notifier.lastKey).isEqualTo(HomesMessageKey.HOME_UNINVITED);
    }

    // --- ListHomeInvites ---

    @Test
    void listHomeInvitesExposesTheGuestSet() {
        invites.addInvite(OWNER, SLOT, GUEST.uuid());
        ListHomeInvites useCase = new ListHomeInvites(invites);

        Set<UUID> guests = useCase.of(OWNER, SLOT);

        assertThat(guests).containsExactly(GUEST.uuid());
    }

    private static Home home() {
        return Home.create(OWNER, SLOT, Position.of(WORLD, 1, 64, 1), CLOCK.instant());
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

    /** In-memory invite store keyed by (owner, slot). */
    private static final class FakeInviteRepository implements HomeInviteRepository {
        private final Map<UUID, Map<HomeSlot, Set<UUID>>> store = new HashMap<>();

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

    /** Records the last key and placeholders sent through the notifier. */
    private static final class CapturingNotifier {
        private @Nullable MessageKey lastKey;
        private Map<String, String> lastPlaceholders = Map.of();

        Notifier notifier() {
            Messages messages = (viewer, key, placeholders) -> {
                lastKey = key;
                lastPlaceholders = placeholders;
                return key.key();
            };
            MessageSink sink = (viewer, renderedText) -> {};
            return new Notifier(messages, sink);
        }
    }

    private static final class RecordingPublisher implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }
}
