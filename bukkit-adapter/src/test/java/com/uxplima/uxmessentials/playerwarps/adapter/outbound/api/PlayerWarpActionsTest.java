package com.uxplima.uxmessentials.playerwarps.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.playerwarps.application.ArchivePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.EditPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpQuota;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.WarpAuthorization;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpPasswordStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.playerwarps.support.InMemoryPlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.support.NoWarpMembers;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published player-warp writes: they run the same use cases the commands do, so the per-warp roles, the owner's
 * limit and the archive/delete split all hold over the API exactly as they do in game.
 */
class PlayerWarpActionsTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef STRANGER = new PlayerRef(UUID.randomUUID(), "Mallory");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final UxmLocation SOMEWHERE = new UxmLocation("world", 10, 64, -20, 0f, 0f);

    private InMemoryPlayerWarpRepository repository;
    private ActionDoubles.InlineScheduler scheduler;
    private PlayerWarpActions actions;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPlayerWarpRepository();
        scheduler = new ActionDoubles.InlineScheduler();
        actions = new PlayerWarpActions(
                setWarp(), edit(), archive(), lookup(), new ActionDoubles.NamedWorlds().with(WORLD), scheduler);
    }

    @Test
    void createStoresTheWarpOffTheCallingThread() {
        UxmOutcome outcome = actions.create(OWNER.uuid(), "shop", SOMEWHERE).join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(repository.findByName(new PlayerWarpName("shop"))).isPresent();
        assertThat(scheduler.asyncCalls()).isOne();
        assertThat(scheduler.entityCalls()).isZero();
    }

    @Test
    void aWorldNobodyLoadedIsAnsweredNotScheduled() {
        UxmOutcome outcome = actions.create(OWNER.uuid(), "shop", new UxmLocation("nether", 0, 64, 0, 0f, 0f))
                .join();

        assertThat(outcome.failureOrThrow().code()).isEqualTo(UxmFailure.NOT_FOUND);
        assertThat(scheduler.asyncCalls()).isZero();
    }

    @Test
    void aNameNoWarpCouldHaveIsRefusedBeforeAnythingIsRead() {
        UxmOutcome outcome = actions.archive(OWNER.uuid(), "no").join();

        assertThat(outcome.failureOrThrow().code()).isEqualTo(UxmFailure.REFUSED);
        assertThat(scheduler.asyncCalls()).isZero();
    }

    @Test
    void archiveRetiresTheWarpAndRestoreBringsItBack() {
        actions.create(OWNER.uuid(), "shop", SOMEWHERE).join();

        assertThat(actions.archive(OWNER.uuid(), "shop").join().succeeded()).isTrue();
        assertThat(stored("shop").status()).isEqualTo(WarpStatus.ARCHIVED);

        assertThat(actions.restore(OWNER.uuid(), "shop").join().succeeded()).isTrue();
        assertThat(stored("shop").status()).isEqualTo(WarpStatus.ACTIVE);
    }

    @Test
    void deleteDropsTheRowForGood() {
        actions.create(OWNER.uuid(), "shop", SOMEWHERE).join();

        assertThat(actions.delete(OWNER.uuid(), "shop").join().succeeded()).isTrue();
        assertThat(repository.findByName(new PlayerWarpName("shop"))).isEmpty();
    }

    @Test
    void aStrangerIsRefusedTheSameWayTheCommandRefusesThem() {
        actions.create(OWNER.uuid(), "shop", SOMEWHERE).join();

        UxmOutcome outcome = actions.delete(STRANGER.uuid(), "shop").join();

        assertThat(outcome.failureOrThrow().code()).isEqualTo(UxmFailure.REFUSED);
        assertThat(repository.findByName(new PlayerWarpName("shop"))).isPresent();
    }

    @Test
    void renamingOntoANameSomebodyElseHoldsIsAlreadyExists() {
        actions.create(OWNER.uuid(), "shop", SOMEWHERE).join();
        actions.create(OWNER.uuid(), "base", SOMEWHERE).join();

        UxmOutcome outcome = actions.rename(OWNER.uuid(), "shop", "base").join();

        assertThat(outcome.failureOrThrow().code()).isEqualTo(UxmFailure.ALREADY_EXISTS);
    }

    @Test
    void renameAndRelocateGoThroughTheEditUseCase() {
        actions.create(OWNER.uuid(), "shop", SOMEWHERE).join();

        assertThat(actions.rename(OWNER.uuid(), "shop", "market").join().succeeded())
                .isTrue();
        assertThat(actions.relocate(OWNER.uuid(), "market", new UxmLocation("world", 1, 2, 3, 0f, 0f))
                        .join()
                        .succeeded())
                .isTrue();

        PlayerWarp moved = stored("market");
        assertThat(moved.location().x()).isEqualTo(1);
        assertThat(moved.location().z()).isEqualTo(3);
    }

    private PlayerWarp stored(String name) {
        return repository.findByName(new PlayerWarpName(name)).orElseThrow();
    }

    private SetPlayerWarp setWarp() {
        return new SetPlayerWarp(
                repository,
                new PlayerWarpQuota(new Unlimited(), 0),
                ActionDoubles.silentNotifier(),
                event -> {},
                clock(),
                List.of());
    }

    private EditPlayerWarp edit() {
        return new EditPlayerWarp(
                repository, authorization(), ActionDoubles.silentNotifier(), new NoPasswords(), clock());
    }

    private ArchivePlayerWarp archive() {
        return new ArchivePlayerWarp(repository, authorization(), ActionDoubles.silentNotifier(), event -> {}, clock());
    }

    private static WarpAuthorization authorization() {
        return new WarpAuthorization(new NoWarpMembers());
    }

    private static QueryDoubles.MapLookup lookup() {
        return new QueryDoubles.MapLookup().with(OWNER).with(STRANGER);
    }

    private static Clock clock() {
        return Clock.fixed(Instant.parse("2026-08-11T12:00:00Z"), ZoneOffset.UTC);
    }

    /** Every player is unlimited here; the limit itself has its own tests in the use case. */
    private static final class Unlimited implements Permissions {

        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.unlimited();
        }
    }

    private static final class NoPasswords implements PlayerWarpPasswordStore {
        @Override
        public void set(PlayerWarpId warp, String plaintext) {}

        @Override
        public void clear(PlayerWarpId warp) {}

        @Override
        public boolean matches(PlayerWarpId warp, String plaintext) {
            return false;
        }
    }
}
