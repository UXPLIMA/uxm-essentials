package com.uxplima.uxmessentials.kits.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmKit;
import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitCost;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published kit query: the catalogue answers from memory because it is configuration, anything about a
 * player waits on a stored read, and "may this player claim it" is the same set of gates the command applies,
 * minus the two that would take something.
 */
class KitQueriesTest {

    private static final PlayerRef PLAYER = new PlayerRef(UUID.randomUUID(), "Alice");

    private FakeKitRepository repository;
    private NodePermissions permissions;
    private StubCooldowns cooldowns;
    private RecordingClaims claims;
    private FixedEconomy economy;
    private QueryDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = new FakeKitRepository();
        permissions = new NodePermissions();
        cooldowns = new StubCooldowns();
        claims = new RecordingClaims();
        economy = new FixedEconomy();
        scheduler = new QueryDoubles.InlineScheduler();
    }

    @Test
    void theCatalogueIsAnsweredFromMemoryWithoutAHopToAWorkerThread() {
        repository.put(kit("starter"));

        assertThat(queries().list()).hasSize(1);
        assertThat(queries().get("starter")).isPresent();
        assertThat(scheduler.asyncCalls())
                .as("the catalogue is configuration held in memory, so reading it costs no scheduling")
                .isZero();
    }

    @Test
    void everyPlayerReadRunsOffTheCallingThread() {
        repository.put(kit("starter"));

        queries().cooldownRemaining(PLAYER.uuid(), "starter").join();
        queries().canClaim(PLAYER.uuid(), "starter").join();
        queries().claimableBy(PLAYER.uuid()).join();

        assertThat(scheduler.asyncCalls()).isEqualTo(3);
    }

    @Test
    void theViewCarriesTheDefinitionTheOperatorWrote() {
        repository.put(kit("vip")
                .withPermission(true)
                .withCooldown(Duration.ofHours(6))
                .withCost(KitCost.of(new BigDecimal("50.00"), "coins"))
                .withCategoryId(Optional.of("ranks"))
                .withFirstJoin(true));

        UxmKit view = queries().get("vip").orElseThrow();

        assertThat(view.id()).isEqualTo("vip");
        assertThat(view.cooldown()).isEqualTo(Duration.ofHours(6));
        assertThat(view.requiresPermission()).isTrue();
        assertThat(view.permissionNode()).contains(KitId.of("vip").permissionNode());
        assertThat(view.cost()).hasValueSatisfying(cost -> {
            assertThat(cost.currency()).isEqualTo("coins");
            assertThat(cost.amount()).isEqualByComparingTo("50.00");
        });
        assertThat(view.isFree()).isFalse();
        assertThat(view.category()).contains("ranks");
        assertThat(view.firstJoin()).isTrue();
        assertThat(view.stockLimit()).isEmpty();
    }

    @Test
    void anUnknownKitIsAbsentAndSoIsAnIdNoKitCouldHave() {
        assertThat(queries().get("nosuchkit")).isEmpty();
        assertThat(queries().get("")).isEmpty();
        assertThat(queries().cooldownRemaining(PLAYER.uuid(), "nosuchkit").join())
                .isEmpty();
        assertThat(queries().canClaim(PLAYER.uuid(), "nosuchkit").join()).isFalse();
    }

    @Test
    void aPlayerWithNoStampHasNoWaitAndOneOnCooldownHasTheRemainder() {
        repository.put(kit("starter"));

        assertThat(queries().cooldownRemaining(PLAYER.uuid(), "starter").join())
                .as("a player who has never claimed it is not waiting for anything")
                .isEmpty();

        cooldowns.remaining = Duration.ofMinutes(90);
        assertThat(queries().cooldownRemaining(PLAYER.uuid(), "starter").join()).contains(Duration.ofMinutes(90));
    }

    @Test
    void aKitGatedBehindANodeThePlayerLacksIsNotClaimable() {
        repository.put(kit("vip").withPermission(true));

        assertThat(queries().canClaim(PLAYER.uuid(), "vip").join()).isFalse();
        assertThat(queries().claimableBy(PLAYER.uuid()).join()).isEmpty();

        permissions.grant(PLAYER, KitId.of("vip").permissionNode());
        assertThat(queries().canClaim(PLAYER.uuid(), "vip").join()).isTrue();
    }

    @Test
    void aKitOnCooldownIsNotClaimableEither() {
        repository.put(kit("starter"));
        cooldowns.remaining = Duration.ofMinutes(5);

        assertThat(queries().canClaim(PLAYER.uuid(), "starter").join()).isFalse();
    }

    @Test
    void aKitThePlayerCannotAffordIsNotClaimable() {
        repository.put(kit("paid").withCost(KitCost.of(new BigDecimal("10.00"), "coins")));
        economy.affordable = false;

        assertThat(queries().canClaim(PLAYER.uuid(), "paid").join()).isFalse();
    }

    @Test
    void askingWhetherAKitIsClaimableTakesNothing() {
        repository.put(kit("paid").withCost(KitCost.of(new BigDecimal("10.00"), "coins")));

        queries().canClaim(PLAYER.uuid(), "paid").join();
        queries().claimableBy(PLAYER.uuid()).join();

        assertThat(economy.withdrawals)
                .as("a question about a kit must never charge for it")
                .isZero();
        assertThat(claims.marked)
                .as("a question about a kit must never spend its one-time claim")
                .isEmpty();
    }

    @Test
    void claimableByListsOnlyWhatThePlayerCouldTake() {
        repository.put(kit("open"));
        repository.put(kit("vip").withPermission(true));

        assertThat(queries().claimableBy(PLAYER.uuid()).join())
                .extracting(UxmKit::id)
                .containsExactly("open");
    }

    private KitQueries queries() {
        KitAccess access = new KitAccess(permissions, cooldowns, claims, Optional.of(economy));
        return new KitQueries(repository, access, new QueryDoubles.MapLookup().with(PLAYER), scheduler);
    }

    private static KitDefinition kit(String id) {
        return KitDefinition.repeatable(KitId.of(id), List.of(), Duration.ofHours(1));
    }

    private static final class FakeKitRepository implements KitRepository {

        private final Map<KitId, KitDefinition> kits = new LinkedHashMap<>();

        void put(KitDefinition kit) {
            kits.put(kit.id(), kit);
        }

        @Override
        public Optional<KitDefinition> find(KitId id) {
            return Optional.ofNullable(kits.get(id));
        }

        @Override
        public List<KitDefinition> all() {
            return new ArrayList<>(kits.values());
        }

        @Override
        public boolean exists(KitId id) {
            return kits.containsKey(id);
        }

        @Override
        public void save(KitDefinition definition) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public void delete(KitId id) {
            throw new AssertionError("a query must never write");
        }
    }

    /** Grants exactly the nodes it was told about, so the permission gate has something to refuse. */
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

    /** Reports one remaining duration for every kit, which is enough to drive the cooldown gate. */
    private static final class StubCooldowns implements Cooldowns {

        private @Nullable Duration remaining;

        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return remaining == null ? Result.ok() : Result.err(remaining);
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {
            throw new AssertionError("a query must never start a cooldown");
        }

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {
            throw new AssertionError("a query must never start a cooldown");
        }
    }

    private static final class RecordingClaims implements KitClaimStore {

        private final Set<String> marked = new HashSet<>();

        @Override
        public boolean hasClaimed(PlayerRef who, KitId kit) {
            return marked.contains(who.uuid() + "|" + kit.value());
        }

        @Override
        public void markClaimed(PlayerRef who, KitId kit) {
            marked.add(who.uuid() + "|" + kit.value());
        }

        @Override
        public void reset(PlayerRef who, KitId kit) {
            throw new AssertionError("a query must never reset a claim");
        }

        @Override
        public void resetAll(PlayerRef who) {
            throw new AssertionError("a query must never reset a claim");
        }
    }

    /** Answers one affordability verdict and counts every withdrawal, of which there should be none. */
    private static final class FixedEconomy implements KitEconomy {

        private boolean affordable = true;
        private int withdrawals;

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return affordable;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            withdrawals++;
            return affordable;
        }

        @Override
        public boolean deposit(PlayerRef who, BigDecimal amount, String currencyId) {
            throw new AssertionError("a query must never deposit");
        }
    }
}
