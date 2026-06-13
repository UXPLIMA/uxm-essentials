package com.uxplima.uxmessentials.homes.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.port.HomeEconomy;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.SethomeGuard;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeCost;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The {@code /sethome} use case under the slot model: an empty guard list creates a home and notifies
 * {@code HOME_CREATED}; hitting the resolved cap publishes {@code HomeLimitReached} and reports the limit;
 * and a guard that returns an error blocks the create before the aggregate is ever touched. The repository,
 * permissions, notifier, and publisher are in-memory fakes so the rule is asserted without any adapter.
 */
class CreateHomeAtSlotTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final int UNLIMITED_MAX_SLOTS = 9;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-30T12:00:00Z"), ZoneOffset.UTC);

    private FakeHomeRepository repository;
    private FakePermissions permissions;
    private CapturingNotifier notifier;
    private RecordingPublisher events;

    @BeforeEach
    void setUp() {
        repository = new FakeHomeRepository();
        permissions = new FakePermissions();
        notifier = new CapturingNotifier();
        events = new RecordingPublisher();
    }

    @Test
    void createWithNoGuardsCreatesTheHomeAndNotifies() {
        permissions.cap = 3;

        Result<Unit, HomeError> result = useCase(List.of()).create(OWNER, HomeSlot.of(0), at(1, 2, 3));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.findSlot(OWNER, HomeSlot.of(0))).isPresent();
        assertThat(notifier.lastKey).isEqualTo(HomesMessageKey.HOME_CREATED);
    }

    @Test
    void createAtTheLimitPublishesLimitReachedAndReportsTheLimit() {
        // A rank downgrade left the owner over their new cap: two homes already set (slots 1 and 5) while
        // the resolved cap is now 1. Slot 0 is free and in range, so the only thing barring it is the cap.
        permissions.cap = 1;
        repository.save(Home.create(OWNER, HomeSlot.of(1), at(0, 64, 0), CLOCK.instant()));
        repository.save(Home.create(OWNER, HomeSlot.of(5), at(0, 64, 0), CLOCK.instant()));

        Result<Unit, HomeError> result = useCase(List.of()).create(OWNER, HomeSlot.of(0), at(4, 5, 6));

        assertThat(result.errorOrThrow()).isEqualTo(HomeError.LIMIT_REACHED);
        assertThat(notifier.lastKey).isEqualTo(HomesMessageKey.HOME_LIMIT_REACHED);
        assertThat(events.published).anyMatch(e -> e.getClass().getSimpleName().equals("HomeLimitReached"));
        assertThat(repository.findSlot(OWNER, HomeSlot.of(0))).isEmpty();
    }

    @Test
    void aGuardThatReturnsAnErrorBlocksTheCreateBeforeTheAggregate() {
        permissions.cap = 3;
        SethomeGuard blocking = (owner, slot, at) -> Result.err(HomeError.SLOT_OUT_OF_RANGE);

        Result<Unit, HomeError> result = useCase(List.of(blocking)).create(OWNER, HomeSlot.of(0), at(1, 2, 3));

        assertThat(result.errorOrThrow()).isEqualTo(HomeError.SLOT_OUT_OF_RANGE);
        assertThat(repository.findSlot(OWNER, HomeSlot.of(0))).isEmpty();
        assertThat(events.published).isEmpty();
        assertThat(notifier.lastKey).isEqualTo(HomesMessageKey.HOME_SLOT_OUT_OF_RANGE);
    }

    @Test
    void createWithUnaffordableChargeReturnsCannotAffordAndDoesNotSave() {
        permissions.cap = 3;
        HomeCost cost = HomeCost.of(new BigDecimal("50.00"));
        HomeChargeSettings chargeSettings = new HomeChargeSettings(cost, cost, cost);
        HomeEconomy cannotAfford = new AlwaysFailEconomy();
        HomeCharge charge = new HomeCharge(permissions, Optional.of(cannotAfford), chargeSettings);

        Result<Unit, HomeError> result = useCase(List.of(), charge).create(OWNER, HomeSlot.of(0), at(1, 2, 3));

        assertThat(result.errorOrThrow()).isEqualTo(HomeError.CANNOT_AFFORD);
        assertThat(repository.findSlot(OWNER, HomeSlot.of(0))).isEmpty();
        assertThat(events.published).isEmpty();
        assertThat(notifier.lastKey).isEqualTo(HomesMessageKey.HOME_CANNOT_AFFORD);
    }

    private CreateHomeAtSlot useCase(List<SethomeGuard> guards) {
        return useCase(guards, freeCharge());
    }

    private CreateHomeAtSlot useCase(List<SethomeGuard> guards, HomeCharge charge) {
        HomeQuota quota = new HomeQuota(permissions, 0);
        return new CreateHomeAtSlot(
                repository, quota, guards, notifier.notifier(), events, charge, UNLIMITED_MAX_SLOTS, CLOCK);
    }

    private HomeCharge freeCharge() {
        return new HomeCharge(permissions, Optional.empty(), HomeChargeSettings.allFree());
    }

    private static Position at(double x, double y, double z) {
        return Position.of(WORLD, x, y, z);
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

        @Override
        public void deleteAll(PlayerRef owner) {
            homes.clear();
        }
    }

    /** A {@link Permissions} fake that resolves a fixed home-limit cap. */
    private static final class FakePermissions implements Permissions {
        private long cap;

        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long fallback) {
            return QuotaResult.limited(cap);
        }
    }

    /** Records every published event. */
    private static final class RecordingPublisher implements DomainEventPublisher {
        private final java.util.List<DomainEvent> published = new java.util.ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    /** Wraps a {@link HomeNotifier} over fakes and records the last key sent. */
    private static final class CapturingNotifier {
        private @Nullable MessageKey lastKey;

        HomeNotifier notifier() {
            Messages messages = (viewer, key, placeholders) -> {
                lastKey = key;
                return key.key();
            };
            MessageSink sink = (viewer, renderedText) -> {};
            return new HomeNotifier(messages, sink);
        }
    }

    /** A {@link HomeEconomy} that always rejects withdrawals. */
    private static final class AlwaysFailEconomy implements HomeEconomy {

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return false;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            return false;
        }
    }
}
