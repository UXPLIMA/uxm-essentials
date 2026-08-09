package com.uxplima.uxmessentials.homes.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmHome;
import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeIcon;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published homes query: it answers off the calling thread, it maps a stored home to the view a consumer sees,
 * it tells "no such slot" apart from "no such player", and it reports the limit the plugin would actually enforce
 * (with unlimited reported as an absent number rather than as a large one).
 */
class HomeQueriesTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final PlayerRef OWNER = new PlayerRef(OWNER_ID, "Alice");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Instant WHEN = Instant.parse("2026-08-09T10:15:00Z");

    private FakeHomeRepository repository;
    private FakePermissions permissions;
    private QueryDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = new FakeHomeRepository();
        permissions = new FakePermissions();
        scheduler = new QueryDoubles.InlineScheduler();
    }

    @Test
    void everyReadRunsOffTheCallingThread() {
        queries().count(OWNER_ID).join();
        queries().list(OWNER_ID).join();
        queries().get(OWNER_ID, 0).join();
        queries().limit(OWNER_ID).join();

        assertThat(scheduler.asyncCalls()).isEqualTo(4);
        assertThat(repository.touchedOnTheCallingThread).isFalse();
    }

    @Test
    void listCarriesEveryStoredFactIntoTheView() {
        repository.put(new Home(
                OWNER,
                HomeSlot.of(2),
                new Position(WORLD, 10.5, 64.0, -20.5, 90f, 15f),
                Optional.of(HomeLabel.of("Base")),
                Optional.of(HomeIcon.of("DIAMOND_BLOCK")),
                true,
                WHEN,
                WHEN.plusSeconds(60)));

        List<UxmHome> homes = queries().list(OWNER_ID).join();

        assertThat(homes).hasSize(1);
        UxmHome home = homes.getFirst();
        assertThat(home.ownerId()).isEqualTo(OWNER_ID);
        assertThat(home.slot()).isEqualTo(2);
        assertThat(home.slotNumber())
                .as("the key counts from zero, the number the owner reads counts from one")
                .isEqualTo(3);
        assertThat(home.displayName()).isEqualTo("Base");
        assertThat(home.location().world()).isEqualTo("world");
        assertThat(home.location().x()).isEqualTo(10.5);
        assertThat(home.location().yaw()).isEqualTo(90f);
        assertThat(home.label()).contains("Base");
        assertThat(home.icon()).contains("DIAMOND_BLOCK");
        assertThat(home.isPublic()).isTrue();
        assertThat(home.createdAt()).isEqualTo(WHEN);
        assertThat(home.updatedAt()).isEqualTo(WHEN.plusSeconds(60));
    }

    @Test
    void aPlayerWithNoHomesIsAnEmptyListRatherThanAFailure() {
        assertThat(queries().list(UUID.randomUUID()).join()).isEmpty();
        assertThat(queries().count(UUID.randomUUID()).join()).isZero();
    }

    @Test
    void anEmptySlotAnswersEmpty() {
        repository.put(home(0));

        assertThat(queries().get(OWNER_ID, 0).join()).isPresent();
        assertThat(queries().get(OWNER_ID, 4).join()).isEmpty();
    }

    @Test
    void countIsTheNumberOfHomesTheOwnerHolds() {
        repository.put(home(0));
        repository.put(home(1));

        assertThat(queries().count(OWNER_ID).join()).isEqualTo(2);
    }

    @Test
    void theLimitIsTheOneTheServerWouldEnforce() {
        permissions.quota = Permissions.QuotaResult.limited(5);

        assertThat(queries().limit(OWNER_ID).join()).contains(5);
    }

    @Test
    void anUnlimitedPlayerHasNoNumberRatherThanAHugeOne() {
        permissions.quota = Permissions.QuotaResult.unlimited();

        assertThat(queries().limit(OWNER_ID).join()).isEmpty();
    }

    @Test
    void aPlayerNobodyHasANameForIsStillAnswered() {
        UUID stranger = UUID.randomUUID();
        repository.put(new Home(
                new PlayerRef(stranger, stranger.toString()),
                HomeSlot.of(0),
                new Position(WORLD, 0, 64, 0, 0f, 0f),
                Optional.empty(),
                Optional.empty(),
                false,
                WHEN,
                WHEN));

        assertThat(queries().list(stranger).join()).hasSize(1);
    }

    @Test
    void aFailingReadReachesTheConsumerRatherThanDyingOnAWorkerThread() {
        repository.explode = true;

        assertThat(queries().count(OWNER_ID)).isCompletedExceptionally();
    }

    @Test
    void aNegativeSlotIsRefusedBeforeAnythingIsScheduled() {
        HomeQueries queries = queries();

        assertThatThrownBy(() -> queries.get(OWNER_ID, -1).join()).isInstanceOf(IllegalArgumentException.class);
        assertThat(scheduler.asyncCalls()).isZero();
    }

    private HomeQueries queries() {
        return new HomeQueries(
                repository,
                new HomeQuota(permissions, 3, Permissions.QuotaReduction.MAX),
                new QueryDoubles.MapLookup().with(OWNER),
                scheduler);
    }

    private static Home home(int slot) {
        return Home.create(OWNER, HomeSlot.of(slot), new Position(WORLD, slot, 64, slot, 0f, 0f), WHEN);
    }

    private static final class FakeHomeRepository implements HomeRepository {

        private final TreeMap<Integer, Home> homes = new TreeMap<>();
        private boolean touchedOnTheCallingThread = true;
        private boolean explode;

        void put(Home home) {
            homes.put(home.slot().index(), home);
        }

        private void read() {
            touchedOnTheCallingThread = false;
            if (explode) {
                throw new IllegalStateException("the database is down");
            }
        }

        @Override
        public HomeSet load(PlayerRef owner) {
            read();
            return HomeSet.of(owner, mine(owner));
        }

        @Override
        public int count(PlayerRef owner) {
            read();
            return mine(owner).size();
        }

        @Override
        public Optional<Home> findSlot(PlayerRef owner, HomeSlot slot) {
            read();
            return Optional.ofNullable(homes.get(slot.index()))
                    .filter(home -> home.owner().equals(owner));
        }

        private List<Home> mine(PlayerRef owner) {
            List<Home> found = new ArrayList<>();
            homes.values().stream().filter(home -> home.owner().equals(owner)).forEach(found::add);
            return found;
        }

        @Override
        public void save(Home home) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public void deleteSlot(PlayerRef owner, HomeSlot slot) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public void deleteAll(PlayerRef owner) {
            throw new AssertionError("a query must never write");
        }
    }

    private static final class FakePermissions implements Permissions {

        private QuotaResult quota = QuotaResult.limited(3);

        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return quota;
        }
    }
}
