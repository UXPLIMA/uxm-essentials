package com.uxplima.uxmessentials.homes.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
 * Access gates on {@code /visit}: the owner always reaches their own home, anyone reaches a public home, an
 * invited player reaches a private home, an uninvited stranger is refused with {@link HomeError#NOT_ACCESSIBLE},
 * and a missing slot is rejected with {@link HomeError#NOT_FOUND}.
 */
class VisitHomeTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Owner");
    private static final PlayerRef VISITOR = new PlayerRef(UUID.randomUUID(), "Visitor");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final HomeSlot SLOT = HomeSlot.of(0);

    private FakeHomeRepository repository;
    private FakeInviteRepository invites;
    private CapturingTeleporter teleporter;
    private VisitHome visitHome;

    @BeforeEach
    void setUp() {
        repository = new FakeHomeRepository();
        invites = new FakeInviteRepository();
        teleporter = new CapturingTeleporter();
        visitHome = new VisitHome(repository, invites, teleporter, silentNotifier());
    }

    @Test
    void ownerReachesTheirOwnPrivateHome() {
        repository.save(privateHome());

        Result<Unit, HomeError> result = visitHome.visit(OWNER, OWNER, SLOT);

        assertThat(result.isOk()).isTrue();
        assertThat(teleporter.teleported()).isTrue();
    }

    @Test
    void nonOwnerReachesAPublicHome() {
        repository.save(privateHome().withVisibility(true, Instant.EPOCH));

        Result<Unit, HomeError> result = visitHome.visit(VISITOR, OWNER, SLOT);

        assertThat(result.isOk()).isTrue();
        assertThat(teleporter.teleported()).isTrue();
    }

    @Test
    void invitedPlayerReachesAPrivateHome() {
        repository.save(privateHome());
        invites.addInvite(OWNER, SLOT, VISITOR.uuid());

        Result<Unit, HomeError> result = visitHome.visit(VISITOR, OWNER, SLOT);

        assertThat(result.isOk()).isTrue();
        assertThat(teleporter.teleported()).isTrue();
    }

    @Test
    void uninvitedStrangerIsRefusedWithNotAccessibleAndNoHop() {
        repository.save(privateHome());

        Result<Unit, HomeError> result = visitHome.visit(VISITOR, OWNER, SLOT);

        assertThat(result.errorOrThrow()).isEqualTo(HomeError.NOT_ACCESSIBLE);
        assertThat(teleporter.teleported()).isFalse();
    }

    @Test
    void aMissingSlotIsRejectedWithNotFound() {
        Result<Unit, HomeError> result = visitHome.visit(VISITOR, OWNER, SLOT);

        assertThat(result.errorOrThrow()).isEqualTo(HomeError.NOT_FOUND);
        assertThat(teleporter.teleported()).isFalse();
    }

    private static Home privateHome() {
        return Home.create(OWNER, SLOT, Position.of(WORLD, 1, 64, 1), Instant.EPOCH);
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

    /** A {@link HomeTeleporter} that records whether it was asked to send anyone anywhere. */
    private static final class CapturingTeleporter implements HomeTeleporter {
        private @Nullable Home lastHome;

        @Override
        public void teleportTo(PlayerRef who, Home home) {
            lastHome = home;
        }

        boolean teleported() {
            return lastHome != null;
        }
    }

    private static HomeNotifier silentNotifier() {
        Messages messages = (viewer, key, placeholders) -> key.key();
        MessageSink sink = (viewer, renderedText) -> {};
        return new HomeNotifier(messages, sink);
    }
}
