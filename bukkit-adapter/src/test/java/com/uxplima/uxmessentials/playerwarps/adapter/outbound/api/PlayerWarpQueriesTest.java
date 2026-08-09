package com.uxplima.uxmessentials.playerwarps.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmPlayerWarp;
import com.uxplima.uxmessentials.api.view.UxmPlayerWarpAccess;
import com.uxplima.uxmessentials.api.view.UxmPlayerWarpStatus;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpQuota;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpBrowse;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.Page;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCard;
import com.uxplima.uxmessentials.playerwarps.domain.WarpQuery;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published player-warp query: the public listing goes through the paged read model rather than loading the
 * table, an owner's own listing includes the warps only they can see, and the view carries the flags a browser
 * needs without carrying the secrets it does not.
 */
class PlayerWarpQueriesTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Instant WHEN = Instant.parse("2026-08-09T10:15:00Z");

    private FakePlayerWarpRepository repository;
    private FakeBrowse browse;
    private FixedQuota permissions;
    private QueryDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = new FakePlayerWarpRepository();
        browse = new FakeBrowse();
        permissions = new FixedQuota();
        scheduler = new QueryDoubles.InlineScheduler();
    }

    @Test
    void everyReadRunsOffTheCallingThread() {
        queries().listPublic(0, 10).join();
        queries().get("shop").join();
        queries().ownedBy(OWNER.uuid()).join();
        queries().count(OWNER.uuid()).join();
        queries().limit(OWNER.uuid()).join();

        assertThat(scheduler.asyncCalls()).isEqualTo(5);
    }

    @Test
    void theViewCarriesWhatABrowserShows() {
        PlayerWarp stored = warp("shop", 7L)
                .withAccess(WarpAccess.PASSWORD, WHEN)
                .withPrice(WarpCost.of(new BigDecimal("15.00"), "coins"), WHEN)
                .withCategoryId(Optional.of("shops"), WHEN);
        repository.put(stored);

        UxmPlayerWarp view = queries().get("shop").join().orElseThrow();

        assertThat(view.id()).isEqualTo(7L);
        assertThat(view.name()).isEqualTo("shop");
        assertThat(view.ownerId()).isEqualTo(OWNER.uuid());
        assertThat(view.ownerName()).isEqualTo("Alice");
        assertThat(view.location().world()).isEqualTo("world");
        assertThat(view.access()).isEqualTo(UxmPlayerWarpAccess.PASSWORD);
        assertThat(view.status()).isEqualTo(UxmPlayerWarpStatus.ACTIVE);
        assertThat(view.category()).contains("shops");
        assertThat(view.price()).hasValueSatisfying(price -> {
            assertThat(price.currency()).isEqualTo("coins");
            assertThat(price.amount()).isEqualByComparingTo("15.00");
        });
        assertThat(view.label()).isEqualTo("shop");
        assertThat(view.isSponsored()).isFalse();
        assertThat(view.createdAt()).isEqualTo(WHEN);
    }

    @Test
    void theListingReadsOnePageThroughTheReadModelRatherThanTheWholeTable() {
        repository.put(warp("one", 1L));
        repository.put(warp("two", 2L));
        browse.page(new PlayerWarpId(1L), new PlayerWarpId(2L));

        List<UxmPlayerWarp> listed = queries().listPublic(0, 10).join();

        assertThat(listed).extracting(UxmPlayerWarp::name).containsExactly("one", "two");
        assertThat(browse.requested).isNotNull();
        assertThat(browse.requested.pageSize()).isEqualTo(10);
        assertThat(repository.listedEverything)
                .as("the paged read model exists so that a listing is never a full-table scan")
                .isFalse();
    }

    @Test
    void aPageSizeTheReadModelWouldRefuseIsRefusedHereToo() {
        assertThatThrownBy(() -> queries().listPublic(0, WarpQuery.MAX_PAGE_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queries().listPublic(-1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThat(scheduler.asyncCalls())
                .as("a refused argument must not reach a worker thread")
                .isZero();
    }

    @Test
    void anOwnerSeesTheirPrivateAndSuspendedWarpsToo() {
        repository.put(warp("hidden", 1L).withStatus(WarpStatus.SUSPENDED, WHEN));

        List<UxmPlayerWarp> owned = queries().ownedBy(OWNER.uuid()).join();

        assertThat(owned).hasSize(1);
        assertThat(owned.getFirst().status()).isEqualTo(UxmPlayerWarpStatus.SUSPENDED);
        assertThat(owned.getFirst().access()).isEqualTo(UxmPlayerWarpAccess.PRIVATE);
    }

    @Test
    void countIsTheNumberTheOwnerHolds() {
        repository.put(warp("one", 1L));
        repository.put(warp("two", 2L));

        assertThat(queries().count(OWNER.uuid()).join()).isEqualTo(2);
    }

    @Test
    void theLimitIsTheOneTheServerWouldEnforceAndUnlimitedHasNoNumber() {
        permissions.quota = Permissions.QuotaResult.limited(4);
        assertThat(queries().limit(OWNER.uuid()).join()).contains(4);

        permissions.quota = Permissions.QuotaResult.unlimited();
        assertThat(queries().limit(OWNER.uuid()).join()).isEmpty();
    }

    @Test
    void anUnknownNameIsAbsentAndSoIsANameNoWarpCouldHave() {
        assertThat(queries().get("nosuchwarp").join()).isEmpty();
        assertThat(queries().get("").join()).isEmpty();
    }

    private PlayerWarpQueries queries() {
        return new PlayerWarpQueries(
                repository,
                browse,
                new PlayerWarpQuota(permissions, 3),
                new QueryDoubles.MapLookup().with(OWNER),
                scheduler);
    }

    private static PlayerWarp warp(String name, long id) {
        return PlayerWarp.create(
                        OWNER, OWNER.name(), new PlayerWarpName(name), new Position(WORLD, 0, 64, 0, 0f, 0f), WHEN)
                .withId(new PlayerWarpId(id));
    }

    private static final class FakePlayerWarpRepository implements PlayerWarpRepository {

        private final Map<Long, PlayerWarp> warps = new LinkedHashMap<>();
        private boolean listedEverything;

        void put(PlayerWarp warp) {
            warps.put(warp.id().orElseThrow().value(), warp);
        }

        @Override
        public Optional<PlayerWarp> findByName(PlayerWarpName name) {
            return warps.values().stream()
                    .filter(warp -> warp.name().equals(name))
                    .findFirst();
        }

        @Override
        public Optional<PlayerWarp> findById(PlayerWarpId id) {
            return Optional.ofNullable(warps.get(id.value()));
        }

        @Override
        public List<PlayerWarp> ownedBy(PlayerRef owner) {
            return warps.values().stream()
                    .filter(warp -> warp.owner().equals(owner))
                    .toList();
        }

        @Override
        public List<PlayerWarp> publicOwnedBy(PlayerRef owner) {
            return ownedBy(owner);
        }

        @Override
        public List<PlayerWarp> all() {
            listedEverything = true;
            return new ArrayList<>(warps.values());
        }

        @Override
        public int count(PlayerRef owner) {
            return ownedBy(owner).size();
        }

        @Override
        public boolean existsByName(PlayerWarpName name) {
            return findByName(name).isPresent();
        }

        @Override
        public PlayerWarpId save(PlayerWarp warp) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public void deleteById(PlayerWarpId id) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public void recordVisit(PlayerWarpId id) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public void updateRating(PlayerWarpId id, RatingSummary summary) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public void refreshFavouriteCount(PlayerWarpId id) {
            throw new AssertionError("a query must never write");
        }
    }

    /** Hands back exactly the ids it was primed with, and remembers what it was asked for. */
    private static final class FakeBrowse implements PlayerWarpBrowse {

        private final List<PlayerWarpId> ids = new ArrayList<>();
        private @Nullable WarpQuery requested;

        void page(PlayerWarpId... primed) {
            ids.addAll(List.of(primed));
        }

        @Override
        public Page<WarpCard> page(WarpQuery query) {
            requested = query;
            List<WarpCard> cards = ids.stream().map(FakeBrowse::card).toList();
            return new Page<>(cards, cards.size(), query.page(), query.pageSize());
        }

        private static WarpCard card(PlayerWarpId id) {
            return new WarpCard(
                    id,
                    "card-" + id.value(),
                    null,
                    OWNER.name(),
                    WORLD.name(),
                    null,
                    null,
                    null,
                    0L,
                    0,
                    0.0,
                    0,
                    0,
                    BigDecimal.ZERO,
                    "default",
                    WarpAccess.PUBLIC,
                    false,
                    false);
        }
    }

    private static final class FixedQuota implements Permissions {

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
