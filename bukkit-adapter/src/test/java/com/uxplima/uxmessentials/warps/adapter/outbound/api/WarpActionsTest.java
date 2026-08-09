package com.uxplima.uxmessentials.warps.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmResult;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmWarp;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.application.DelWarp;
import com.uxplima.uxmessentials.warps.application.MoveWarp;
import com.uxplima.uxmessentials.warps.application.SetWarp;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published warp actions: create refuses a name that exists, move refuses one that does not, and the warp a
 * plugin creates is the same warp {@code /warp} then takes players to.
 */
class WarpActionsTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final UxmLocation SPAWN = new UxmLocation("world", 0, 64, 0);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);

    private FakeWarpRepository repository;
    private ActionDoubles.InlineScheduler scheduler;
    private ActionDoubles.RecordingEvents events;
    private DomainGate gate;

    @BeforeEach
    void setUp() {
        repository = new FakeWarpRepository();
        scheduler = new ActionDoubles.InlineScheduler();
        events = new ActionDoubles.RecordingEvents();
        gate = ActionDoubles.DecidingGate.allowing();
    }

    @Test
    void creatingAWarpStoresItAndAnswersWithWhatItStored() {
        UxmResult<UxmWarp> result = actions().create("shop", SPAWN).join();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.valueOrThrow().name()).isEqualTo("shop");
        assertThat(repository.exists(WarpName.of("shop"))).isTrue();
    }

    @Test
    void theWarpRecordsThePluginThatCreatedIt() {
        UxmWarp created = actions("MyQuests").create("shop", SPAWN).join().valueOrThrow();

        assertThat(created.ownerName())
                .as("an operator reading /warp info should see where the warp came from")
                .isEqualTo("MyQuests");
    }

    @Test
    void creatingAWarpThatExistsIsRefusedRatherThanMovingIt() {
        actions().create("shop", SPAWN).join();

        UxmResult<UxmWarp> second =
                actions().create("shop", new UxmLocation("world", 500, 64, 500)).join();

        assertThat(second.failureOrThrow().is(UxmFailure.ALREADY_EXISTS)).isTrue();
        assertThat(repository.find(WarpName.of("shop")).orElseThrow().location().x())
                .as("the command's set would have moved it; an API create must not")
                .isEqualTo(0);
    }

    @Test
    void movingAWarpThatDoesNotExistIsNotFoundRatherThanCreatingIt() {
        UxmResult<UxmWarp> result = actions().move("nowhere", SPAWN).join();

        assertThat(result.failureOrThrow().is(UxmFailure.NOT_FOUND)).isTrue();
        assertThat(repository.all()).isEmpty();
    }

    @Test
    void movingAWarpAnswersWithWhereItNowIs() {
        actions().create("shop", SPAWN).join();

        UxmResult<UxmWarp> moved =
                actions().move("shop", new UxmLocation("world", 500, 70, 500)).join();

        assertThat(moved.valueOrThrow().location().x()).isEqualTo(500);
    }

    @Test
    void deletingAWarpRemovesItAndDeletingNothingSaysSo() {
        actions().create("shop", SPAWN).join();

        assertThat(actions().delete("shop").join().succeeded()).isTrue();
        assertThat(repository.all()).isEmpty();

        UxmOutcome again = actions().delete("shop").join();
        assertThat(again.failureOrThrow().is(UxmFailure.NOT_FOUND)).isTrue();
    }

    @Test
    void aWorldTheServerHasNotLoadedIsAFailureRatherThanAnException() {
        UxmResult<UxmWarp> result =
                actions().create("shop", new UxmLocation("nowhere", 0, 64, 0)).join();

        assertThat(result.failureOrThrow().is(UxmFailure.NOT_FOUND)).isTrue();
    }

    @Test
    void aNameNoWarpCouldHaveIsRefusedRatherThanStored() {
        assertThat(actions().create("", SPAWN).join().failureOrThrow().is(UxmFailure.REFUSED))
                .isTrue();
        assertThat(actions().delete("").join().failureOrThrow().is(UxmFailure.NOT_FOUND))
                .isTrue();
    }

    @Test
    void anotherPluginRefusingComesBackAsCancelled() {
        gate = ActionDoubles.DecidingGate.refusing();

        assertThat(actions().create("shop", SPAWN).join().failureOrThrow().is(UxmFailure.CANCELLED))
                .isTrue();
        assertThat(repository.all()).isEmpty();
    }

    @Test
    void creatingAWarpPublishesTheFactTheEventBridgeCarries() {
        actions().create("shop", SPAWN).join();

        assertThat(events.published()).isNotEmpty();
    }

    @Test
    void everyWriteRunsOffTheCallingThread() {
        actions().create("shop", SPAWN).join();
        actions().delete("shop").join();

        assertThat(scheduler.asyncCalls()).isEqualTo(2);
    }

    private WarpActions actions() {
        return actions("TestPlugin");
    }

    private WarpActions actions(String source) {
        return new WarpActions(
                new SetWarp(repository, ActionDoubles.silentNotifier(), events, gate, CLOCK, List.of()),
                new MoveWarp(repository, ActionDoubles.silentNotifier()),
                new DelWarp(repository, ActionDoubles.silentNotifier(), events, gate),
                repository,
                new ActionDoubles.NamedWorlds().with(WORLD),
                scheduler,
                source);
    }

    /** A warp store that actually stores, since these tests are about what the write left behind. */
    private static final class FakeWarpRepository implements WarpRepository {

        private final Map<WarpName, Warp> warps = new LinkedHashMap<>();
        private final Map<WarpName, Double> ratings = new LinkedHashMap<>();

        @Override
        public Optional<Warp> find(WarpName name) {
            return Optional.ofNullable(warps.get(name));
        }

        @Override
        public List<Warp> all() {
            return new ArrayList<>(warps.values());
        }

        @Override
        public boolean exists(WarpName name) {
            return warps.containsKey(name);
        }

        @Override
        public void save(Warp warp) {
            warps.put(warp.name(), warp);
        }

        @Override
        public void delete(WarpName name) {
            warps.remove(name);
        }

        @Override
        public void rate(WarpName name, UUID player, double rating) {
            ratings.put(name, rating);
        }

        @Override
        public double averageRating(WarpName name) {
            return ratings.getOrDefault(name, 0.0);
        }
    }
}
