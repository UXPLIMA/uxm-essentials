package com.uxplima.uxmessentials.kits.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.kits.application.ClaimKit;
import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownKind;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
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
 * The published kit actions: {@code give} hands the items over with nothing in the way, {@code claim} runs the
 * player's own path with everything in the way, and both land on the thread that owns the inventory they fill.
 */
class KitActionsTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final UUID STRANGER = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC);

    private FakeKitRepository repository;
    private RecordingGranter granter;
    private StubCooldowns cooldowns;
    private RecordingClaims claims;
    private NodePermissions permissions;
    private ActionDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = new FakeKitRepository();
        repository.put(KitDefinition.repeatable(KitId.of("starter"), List.of(), Duration.ofHours(1)));
        granter = new RecordingGranter();
        cooldowns = new StubCooldowns();
        claims = new RecordingClaims();
        permissions = new NodePermissions();
        scheduler = new ActionDoubles.InlineScheduler();
    }

    @Test
    void givingHandsTheItemsOverWithNoGateInTheWay() {
        UxmOutcome outcome = actions().give(ALICE.uuid(), "starter").join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(granter.grants).containsExactly("starter");
        assertThat(cooldowns.stamped)
                .as("a plugin handing out a kit is not the player claiming it, so no cooldown starts")
                .isFalse();
    }

    @Test
    void givingLandsOnThePlayersOwnThread() {
        actions().give(ALICE.uuid(), "starter").join();

        assertThat(scheduler.entityCalls())
                .as("the items go into a live inventory, which only that player's thread may touch")
                .isEqualTo(1);
    }

    @Test
    void claimingRunsEveryGateAndSaysWhichOneRefused() {
        cooldowns.remaining = Duration.ofMinutes(5);

        UxmOutcome outcome = actions().claim(ALICE.uuid(), "starter").join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.REFUSED)).isTrue();
        assertThat(granter.grants).isEmpty();
    }

    @Test
    void claimingWithEveryGatePassedHandsTheKitOverAndStartsTheCooldown() {
        permissions.grant(ALICE, "uxmessentials.kit.starter");

        assertThat(actions().claim(ALICE.uuid(), "starter").join().succeeded()).isTrue();
        assertThat(granter.grants).containsExactly("starter");
        assertThat(cooldowns.stamped)
                .as("a claim is the player's own path, and that path starts the clock")
                .isTrue();
    }

    @Test
    void aKitNobodyDefinedIsNotFound() {
        assertThat(actions()
                        .give(ALICE.uuid(), "nosuchkit")
                        .join()
                        .failureOrThrow()
                        .is(UxmFailure.NOT_FOUND))
                .isTrue();
    }

    @Test
    void anOfflinePlayerIsSaidToBeOfflineRatherThanLosingTheItems() {
        UxmOutcome outcome = actions().give(STRANGER, "starter").join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.PLAYER_OFFLINE)).isTrue();
        assertThat(granter.grants).isEmpty();
    }

    @Test
    void aPlayerWhoLeavesBeforeTheHopCompletesTheFutureAnyway() {
        // Without the retired path the future would never complete, and a consumer chaining off it would simply
        // stop: no items, no failure, nothing in the log.
        scheduler.retire(ALICE);

        UxmOutcome outcome = actions().give(ALICE.uuid(), "starter").join();

        assertThat(outcome.failureOrThrow().is(UxmFailure.PLAYER_OFFLINE)).isTrue();
    }

    private KitActions actions() {
        KitAccess access = new KitAccess(permissions, cooldowns, claims, Optional.empty());
        ClaimKit claim = new ClaimKit(
                repository,
                access,
                granter,
                ActionDoubles.silentNotifier(),
                new ActionDoubles.RecordingEvents(),
                CLOCK,
                Optional.empty(),
                Optional.empty(),
                DomainGate.allowAll());
        return new KitActions(repository, granter, claim, new QueryDoubles.MapLookup().with(ALICE), scheduler);
    }

    /** Remembers which kits it was told to hand over, which is the only visible effect either verb has. */
    private static final class RecordingGranter implements KitGranter {

        private final List<String> grants = new ArrayList<>();

        @Override
        public Grant grant(PlayerRef recipient, KitDefinition kit) {
            grants.add(kit.id().value());
            return Grant.complete();
        }
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
            kits.put(definition.id(), definition);
        }

        @Override
        public void delete(KitId id) {
            kits.remove(id);
        }
    }

    /** Grants nothing until a test says otherwise, so the permission gate is exercised. */
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

    /** One remaining duration for every kit, and a record of whether the clock was ever started. */
    private static final class StubCooldowns implements Cooldowns {

        private @Nullable Duration remaining;
        private boolean stamped;

        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return remaining == null ? Result.ok() : Result.err(remaining);
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {
            stamped = true;
        }

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return remaining == null ? Result.ok() : Result.err(remaining);
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {
            stamped = true;
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
            marked.remove(who.uuid() + "|" + kit.value());
        }

        @Override
        public void resetAll(PlayerRef who) {
            marked.clear();
        }
    }
}
