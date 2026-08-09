package com.uxplima.uxmessentials.warps.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmWarp;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import com.uxplima.uxmessentials.warps.application.ListWarps;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published server-warp query: it answers off the calling thread, it hides what the {@code /warps} filter
 * hides, it publishes that a warp has a password without publishing the password, and a name no warp could ever
 * have is an absent warp rather than a stack trace.
 */
class WarpQueriesTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef VIEWER = new PlayerRef(UUID.randomUUID(), "Bob");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Instant WHEN = Instant.parse("2026-08-09T10:15:00Z");

    private FakeWarpRepository repository;
    private NodePermissions permissions;
    private QueryDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = new FakeWarpRepository();
        permissions = new NodePermissions();
        scheduler = new QueryDoubles.InlineScheduler();
    }

    @Test
    void everyReadRunsOffTheCallingThread() {
        queries().list().join();
        queries().get("spawn").join();
        queries().exists("spawn").join();
        queries().visibleTo(VIEWER.uuid()).join();
        queries().averageRating("spawn").join();

        assertThat(scheduler.asyncCalls()).isEqualTo(5);
    }

    @Test
    void listCarriesEveryStoredFactIntoTheView() {
        repository.put(new Warp(
                WarpName.of("shop"),
                new Position(WORLD, 10.5, 64.0, -20.5, 90f, 15f),
                OWNER,
                WHEN,
                WarpCost.of(new BigDecimal("25.00"), "coins"),
                Optional.of("uxmessentials.warp.vip"),
                42L,
                Optional.of("hashed-secret"),
                true,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of("DIAMOND_BLOCK"),
                Optional.of("shops")));

        UxmWarp warp = queries().list().join().getFirst();

        assertThat(warp.name()).isEqualTo("shop");
        assertThat(warp.location().world()).isEqualTo("world");
        assertThat(warp.location().x()).isEqualTo(10.5);
        assertThat(warp.ownerId()).isEqualTo(OWNER.uuid());
        assertThat(warp.ownerName()).isEqualTo("Alice");
        assertThat(warp.createdAt()).isEqualTo(WHEN);
        assertThat(warp.cost()).hasValueSatisfying(cost -> {
            assertThat(cost.currency()).isEqualTo("coins");
            assertThat(cost.amount()).isEqualByComparingTo("25.00");
        });
        assertThat(warp.isFree()).isFalse();
        assertThat(warp.requiredPermission()).contains("uxmessentials.warp.vip");
        assertThat(warp.visitors()).isEqualTo(42L);
        assertThat(warp.locked()).isTrue();
        assertThat(warp.category()).contains("shops");
        assertThat(warp.icon()).contains("DIAMOND_BLOCK");
    }

    @Test
    void aPasswordIsPublishedAsAFlagAndNeverAsAValue() {
        repository.put(warp("secret", Optional.of("hashed-secret")));

        UxmWarp warp = queries().get("secret").join().orElseThrow();

        assertThat(warp.passwordProtected()).isTrue();
        assertThat(warp.toString())
                .as("nothing in the view should carry the stored password anywhere a consumer can read it")
                .doesNotContain("hashed-secret");
    }

    @Test
    void aFreeWarpHasNoCostRatherThanAZeroOne() {
        repository.put(warp("spawn", Optional.empty()));

        assertThat(queries().get("spawn").join().orElseThrow().cost()).isEmpty();
    }

    @Test
    void visibleToAppliesTheSameFilterTheCommandDoes() {
        repository.put(warp("open", Optional.empty()));
        repository.put(warp("vip", Optional.empty()));
        permissions.grant(VIEWER, WarpName.of("open").useNode());

        List<UxmWarp> visible = queries().visibleTo(VIEWER.uuid()).join();

        assertThat(visible).extracting(UxmWarp::name).containsExactly("open");
        assertThat(queries().list().join())
                .as("the unfiltered list still holds both, so the filter is the only thing hiding one")
                .hasSize(2);
    }

    @Test
    void anUnknownNameIsAnAbsentWarp() {
        assertThat(queries().get("nosuchwarp").join()).isEmpty();
        assertThat(queries().exists("nosuchwarp").join()).isFalse();
    }

    @Test
    void aNameNoWarpCouldEverHaveIsAlsoJustAbsent() {
        // Warp names are bounded and validated, so this one can never name a row. A consumer passing along
        // whatever a player typed gets "no such warp", which is what the command says too.
        String tooLong = "w".repeat(WarpName.MAX_LENGTH + 1);

        assertThat(queries().get(tooLong).join()).isEmpty();
        assertThat(queries().exists(tooLong).join()).isFalse();
        assertThat(queries().averageRating(tooLong).join()).isZero();
    }

    @Test
    void theRatingIsReadOnDemandRatherThanCarriedOnEveryWarp() {
        repository.put(warp("shop", Optional.empty()));
        repository.rate(WarpName.of("shop"), UUID.randomUUID(), 4.0);

        assertThat(queries().averageRating("shop").join()).isEqualTo(4.0);
        assertThat(repository.ratingReads)
                .as("listing warps must not cost one rating query per warp")
                .isEqualTo(1);
    }

    private WarpQueries queries() {
        Notifier notifier = new Notifier(new KeyMessages(), new DiscardingSink());
        return new WarpQueries(
                repository,
                new ListWarps(repository, permissions, notifier),
                new QueryDoubles.MapLookup().with(VIEWER).with(OWNER),
                scheduler);
    }

    private static Warp warp(String name, Optional<String> password) {
        return new Warp(
                WarpName.of(name),
                new Position(WORLD, 0, 64, 0, 0f, 0f),
                OWNER,
                WHEN,
                WarpCost.free(),
                Optional.empty(),
                0L,
                password,
                false,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static final class FakeWarpRepository implements WarpRepository {

        private final Map<WarpName, Warp> warps = new LinkedHashMap<>();
        private final Map<WarpName, Double> ratings = new LinkedHashMap<>();
        private int ratingReads;

        void put(Warp warp) {
            warps.put(warp.name(), warp);
        }

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
            throw new AssertionError("a query must never write");
        }

        @Override
        public void delete(WarpName name) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public void rate(WarpName name, UUID player, double rating) {
            ratings.put(name, rating);
        }

        @Override
        public double averageRating(WarpName name) {
            ratingReads++;
            return ratings.getOrDefault(name, 0.0);
        }
    }

    /** Grants exactly the nodes it was told about, so the warp filter has something to hide behind. */
    private static final class NodePermissions implements Permissions {

        private final Set<String> granted = new HashSet<>();

        void grant(PlayerRef who, String node) {
            granted.add(who.uuid() + "|" + node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted.contains(who.uuid() + "|" + node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** The query never notifies anyone; this exists only so the use case can be constructed. */
    private static final class DiscardingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }
}
