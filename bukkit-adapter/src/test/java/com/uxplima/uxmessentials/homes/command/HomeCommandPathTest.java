package com.uxplima.uxmessentials.homes.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.DelHome;
import com.uxplima.uxmessentials.homes.application.HomeNotifier;
import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.SetHome;
import com.uxplima.uxmessentials.homes.application.TeleportHome;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The homes command paths through the real use cases against an in-memory repository and a recording
 * teleporter — the same wiring the Brigadier handlers drive, minus Bukkit. It proves a {@code /sethome}
 * persists a row and refuses past the resolved limit, that {@code /home} delegates execution to the
 * teleport context rather than moving the player itself, and that {@code /delhome} frees a slot. The quota
 * is resolved through a stub {@link Permissions} so the numbered-node reducer's contract is exercised at
 * the seam the homes context consumes.
 */
class HomeCommandPathTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeHomeRepository repository;
    private RecordingTeleporter teleporter;
    private StubPermissions permissions;
    private SetHome setHome;
    private DelHome delHome;
    private TeleportHome teleportHome;
    private PlayerRef alice;

    @BeforeEach
    void setUp() {
        repository = new FakeHomeRepository();
        teleporter = new RecordingTeleporter();
        permissions = new StubPermissions();
        HomeNotifier notifier = new HomeNotifier(new KeyMessages(), new CapturingSink());
        HomeQuota quota = new HomeQuota(permissions, 1); // config default of one home
        DomainEventPublisher events = new CapturingEvents();
        setHome = new SetHome(repository, quota, notifier, events, Clock.system(ZoneOffset.UTC));
        delHome = new DelHome(repository, notifier, events);
        teleportHome = new TeleportHome(repository, teleporter, notifier);
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
    }

    @Test
    void setHomePersistsARowWithinTheLimit() {
        Result<Unit, HomeError> result = setHome.set(alice, HomeName.of("base"), at(10, 64, 20));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.count(alice)).isEqualTo(1);
        assertThat(repository.load(alice).find(HomeName.of("base"))).isPresent();
    }

    @Test
    void setHomePastTheResolvedLimitIsRefused() {
        setHome.set(alice, HomeName.of("base"), at(0, 0, 0)); // fills the default limit of one

        Result<Unit, HomeError> second = setHome.set(alice, HomeName.of("camp"), at(1, 1, 1));

        assertThat(second.isErr()).isTrue();
        assertThat(second.errorOrThrow()).isEqualTo(HomeError.LIMIT_REACHED);
        assertThat(repository.count(alice)).isEqualTo(1);
    }

    @Test
    void aHigherLimitNodeRaisesTheCap() {
        permissions.quota = Permissions.QuotaResult.limited(5); // uxmessentials.home.limit.5

        setHome.set(alice, HomeName.of("base"), at(0, 0, 0));
        Result<Unit, HomeError> second = setHome.set(alice, HomeName.of("camp"), at(1, 1, 1));

        assertThat(second.isOk()).isTrue();
        assertThat(repository.count(alice)).isEqualTo(2);
    }

    @Test
    void homeDelegatesExecutionToTheTeleportContext() {
        setHome.set(alice, HomeName.of("base"), at(7, 64, 7));

        Result<Unit, HomeError> result = teleportHome.toNamed(alice, HomeName.of("base"));

        assertThat(result.isOk()).isTrue();
        assertThat(teleporter.hops).isEqualTo(1); // the hop was delegated, not performed here
        assertThat(teleporter.lastHome.name()).isEqualTo(HomeName.of("base"));
    }

    @Test
    void homeWithNoHomesSetIsRejected() {
        Result<Unit, HomeError> result = teleportHome.toDefault(alice);

        assertThat(result.errorOrThrow()).isEqualTo(HomeError.NONE_SET);
        assertThat(teleporter.hops).isZero();
    }

    @Test
    void delHomeFreesASlotUnderTheLimit() {
        setHome.set(alice, HomeName.of("base"), at(0, 0, 0));

        delHome.delete(alice, HomeName.of("base"));
        Result<Unit, HomeError> resettable = setHome.set(alice, HomeName.of("camp"), at(1, 1, 1));

        assertThat(resettable.isOk()).isTrue();
        assertThat(repository.count(alice)).isEqualTo(1);
    }

    private static Position at(double x, double y, double z) {
        return Position.of(WORLD, x, y, z);
    }

    /** A map-backed {@link HomeRepository} that keeps an owner's homes in insertion order. */
    private static final class FakeHomeRepository implements HomeRepository {
        private final Map<UUID, Map<String, Home>> byOwner = new HashMap<>();

        @Override
        public HomeSet load(PlayerRef owner) {
            return HomeSet.of(owner, new java.util.ArrayList<>(owned(owner).values()));
        }

        @Override
        public int count(PlayerRef owner) {
            return owned(owner).size();
        }

        @Override
        public void save(Home home) {
            owned(home.owner()).put(home.name().value(), home);
        }

        @Override
        public void rename(PlayerRef owner, HomeName from, HomeName to) {
            Home existing = owned(owner).remove(from.value());
            if (existing != null) {
                owned(owner).put(to.value(), existing.renamedTo(to));
            }
        }

        @Override
        public void delete(PlayerRef owner, HomeName name) {
            owned(owner).remove(name.value());
        }

        private Map<String, Home> owned(PlayerRef owner) {
            return byOwner.computeIfAbsent(owner.uuid(), u -> new java.util.LinkedHashMap<>());
        }
    }

    private static final class RecordingTeleporter implements HomeTeleporter {
        int hops;
        private Home lastHome = new Home(
                new PlayerRef(new UUID(0L, 0L), "none"),
                HomeName.of("none"),
                Position.of(WORLD, 0, 0, 0),
                java.time.Instant.EPOCH);

        @Override
        public void teleportTo(PlayerRef who, Home home) {
            hops++;
            lastHome = home;
        }
    }

    /** A stub permissions port whose quota is the home limit the reducer would return. */
    private static final class StubPermissions implements Permissions {
        private QuotaResult quota = QuotaResult.limited(1);

        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @org.jspecify.annotations.Nullable WorldRef world, long fallback) {
            return quota;
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class CapturingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // discarded: feedback delivery is not under test here
        }
    }

    private static final class CapturingEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {
            // discarded: event publication is asserted in the domain tests
        }
    }
}
