package com.uxplima.uxmessentials.homes.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmResult;
import com.uxplima.uxmessentials.api.view.UxmHome;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.homes.application.CreateHomeAtSlot;
import com.uxplima.uxmessentials.homes.application.DeleteHome;
import com.uxplima.uxmessentials.homes.application.HomeCharge;
import com.uxplima.uxmessentials.homes.application.HomeChargeSettings;
import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.RelocateHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaReduction;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published home actions: a plugin can set, move, name and remove a player's homes, the write runs the same
 * use case the command runs, and every refusal comes back as a code rather than as an exception.
 */
class HomeActionsTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final UxmLocation SOMEWHERE = new UxmLocation("world", 10, 64, -10);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);

    private FakeRepository repository;
    private FakeInvites invites;
    private FakePermissions permissions;
    private ActionDoubles.InlineScheduler scheduler;
    private ActionDoubles.RecordingEvents events;
    private DomainGate gate;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        invites = new FakeInvites();
        permissions = new FakePermissions();
        scheduler = new ActionDoubles.InlineScheduler();
        events = new ActionDoubles.RecordingEvents();
        gate = ActionDoubles.DecidingGate.allowing();
    }

    @Test
    void settingAHomeStoresItAndAnswersWithWhatItStored() {
        UxmResult<UxmHome> result = actions().set(ALICE.uuid(), 0, SOMEWHERE).join();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.valueOrThrow().slot()).isZero();
        assertThat(result.valueOrThrow().location().world()).isEqualTo("world");
        assertThat(repository.findSlot(ALICE, HomeSlot.of(0))).isPresent();
    }

    @Test
    void settingAHomePublishesTheFactTheEventBridgeCarries() {
        actions().set(ALICE.uuid(), 0, SOMEWHERE).join();

        assertThat(events.published())
                .as("an API write is a write like any other, so whoever listens for homes still hears it")
                .isNotEmpty();
    }

    @Test
    void aSlotThatAlreadyHoldsAHomeIsRefusedRatherThanOverwritten() {
        actions().set(ALICE.uuid(), 0, SOMEWHERE).join();

        UxmResult<UxmHome> second = actions()
                .set(ALICE.uuid(), 0, new UxmLocation("world", 99, 64, 99))
                .join();

        assertThat(second.failureOrThrow().is(UxmFailure.ALREADY_EXISTS)).isTrue();
        assertThat(repository
                        .findSlot(ALICE, HomeSlot.of(0))
                        .orElseThrow()
                        .location()
                        .x())
                .as("a refused set leaves the home exactly where it was")
                .isEqualTo(10);
    }

    @Test
    void movingAHomeIsRelocateAndAnswersWithTheNewPlace() {
        actions().set(ALICE.uuid(), 0, SOMEWHERE).join();

        UxmResult<UxmHome> moved = actions()
                .relocate(ALICE.uuid(), 0, new UxmLocation("world", 99, 70, 99))
                .join();

        assertThat(moved.valueOrThrow().location().x()).isEqualTo(99);
    }

    @Test
    void aWorldTheServerHasNotLoadedIsAFailureRatherThanAnException() {
        UxmResult<UxmHome> result = actions()
                .set(ALICE.uuid(), 0, new UxmLocation("nowhere", 0, 64, 0))
                .join();

        assertThat(result.failureOrThrow().is(UxmFailure.NOT_FOUND)).isTrue();
    }

    @Test
    void anotherPluginRefusingComesBackAsCancelled() {
        gate = ActionDoubles.DecidingGate.refusing();

        assertThat(actions()
                        .set(ALICE.uuid(), 0, SOMEWHERE)
                        .join()
                        .failureOrThrow()
                        .is(UxmFailure.CANCELLED))
                .as("the veto path the plugin already had works the same way for a plugin's own write")
                .isTrue();
        assertThat(repository.findSlot(ALICE, HomeSlot.of(0))).isEmpty();
    }

    @Test
    void thePlayersOwnLimitStillApplies() {
        permissions.quota = QuotaResult.limited(1);
        actions().set(ALICE.uuid(), 0, SOMEWHERE).join();

        UxmOutcome second = actions().set(ALICE.uuid(), 1, SOMEWHERE).join().asOutcome();

        assertThat(second.failureOrThrow().is(UxmFailure.REFUSED))
                .as("a home past the limit would be one the player cannot see, so the limit is not ours to skip")
                .isTrue();
    }

    @Test
    void namingAHomeIsRememberedAndDeletingItRemovesIt() {
        actions().set(ALICE.uuid(), 0, SOMEWHERE).join();

        assertThat(actions().rename(ALICE.uuid(), 0, "Base").join().succeeded()).isTrue();
        assertThat(repository.findSlot(ALICE, HomeSlot.of(0)).orElseThrow().label())
                .hasValueSatisfying(label -> assertThat(label.value()).isEqualTo("Base"));

        assertThat(actions().delete(ALICE.uuid(), 0).join().succeeded()).isTrue();
        assertThat(repository.findSlot(ALICE, HomeSlot.of(0))).isEmpty();
    }

    @Test
    void deletingAHomeThatIsNotThereSaysSo() {
        assertThat(actions().delete(ALICE.uuid(), 3).join().failureOrThrow().is(UxmFailure.NOT_FOUND))
                .isTrue();
    }

    @Test
    void aNegativeSlotIsACallerBugAndThrows() {
        assertThatThrownBy(() -> actions().delete(ALICE.uuid(), -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyWriteRunsOffTheCallingThread() {
        actions().set(ALICE.uuid(), 0, SOMEWHERE).join();
        actions().delete(ALICE.uuid(), 0).join();

        assertThat(scheduler.asyncCalls()).isEqualTo(2);
    }

    private HomeActions actions() {
        HomeCharge free = new HomeCharge(permissions, Optional.empty(), HomeChargeSettings.allFree());
        HomeQuota quota = new HomeQuota(permissions, 3, QuotaReduction.MAX);
        HomeApiWrites writes = new HomeApiWrites(
                new CreateHomeAtSlot(
                        repository,
                        invites,
                        quota,
                        List.of(),
                        ActionDoubles.silentNotifier(),
                        events,
                        gate,
                        free,
                        100,
                        CLOCK),
                new RelocateHome(repository, List.of(), ActionDoubles.silentNotifier(), events, gate, free, CLOCK),
                new RenameHome(repository, ActionDoubles.silentNotifier(), events, CLOCK),
                new DeleteHome(repository, invites, ActionDoubles.silentNotifier(), events, gate));
        return new HomeActions(
                writes,
                repository,
                new QueryDoubles.MapLookup().with(ALICE),
                new ActionDoubles.NamedWorlds().with(WORLD),
                scheduler);
    }

    /** A home store that actually stores, since these tests are about what the write left behind. */
    private static final class FakeRepository implements HomeRepository {

        private final TreeMap<Integer, Home> homes = new TreeMap<>();

        @Override
        public HomeSet load(PlayerRef owner) {
            return HomeSet.of(owner, mine(owner));
        }

        @Override
        public int count(PlayerRef owner) {
            return mine(owner).size();
        }

        @Override
        public Optional<Home> findSlot(PlayerRef owner, HomeSlot slot) {
            return Optional.ofNullable(homes.get(slot.index()))
                    .filter(home -> home.owner().equals(owner));
        }

        @Override
        public void save(Home home) {
            homes.put(home.slot().index(), home);
        }

        @Override
        public void deleteSlot(PlayerRef owner, HomeSlot slot) {
            homes.remove(slot.index());
        }

        @Override
        public void deleteAll(PlayerRef owner) {
            homes.clear();
        }

        private List<Home> mine(PlayerRef owner) {
            List<Home> found = new ArrayList<>();
            homes.values().stream().filter(home -> home.owner().equals(owner)).forEach(found::add);
            return found;
        }
    }

    /** The invite rows a create clears and a delete removes; nothing here reads them back. */
    private static final class FakeInvites implements HomeInviteRepository {

        @Override
        public java.util.Set<UUID> invites(PlayerRef owner, HomeSlot slot) {
            return java.util.Set.of();
        }

        @Override
        public void addInvite(PlayerRef owner, HomeSlot slot, UUID invited) {}

        @Override
        public void removeInvite(PlayerRef owner, HomeSlot slot, UUID invited) {}

        @Override
        public void removeAll(PlayerRef owner, HomeSlot slot) {}

        @Override
        public void removeAllForOwner(PlayerRef owner) {}
    }

    /** Grants nothing but the home limit, which is the one gate these tests care about. */
    private static final class FakePermissions implements Permissions {

        private QuotaResult quota = QuotaResult.limited(3);

        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who,
                Permissions.QuotaFamily family,
                @org.jspecify.annotations.Nullable WorldRef world,
                long configDefault) {
            return quota;
        }
    }
}
