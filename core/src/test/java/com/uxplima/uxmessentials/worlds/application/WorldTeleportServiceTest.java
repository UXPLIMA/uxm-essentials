package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.worlds.application.port.WorldEntryFee;
import com.uxplima.uxmessentials.worlds.application.port.WorldTeleporter;
import com.uxplima.uxmessentials.worlds.domain.AccessDecision;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import com.uxplima.uxmessentials.worlds.domain.WorldTeleportCause;
import com.uxplima.uxmessentials.worlds.domain.event.WorldEntryDenied;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * {@code WorldTeleportService} is the entry point both for self-service {@code /worlds spawn} (access-gated and
 * fee-charged) and for the staff {@code /worlds tp} override (no gate, no fee). The truth table covered here:
 * a missing world never hands off and never charges; an unloaded world is loaded on demand before the hand-off;
 * a denied access decision publishes {@link WorldEntryDenied} and refuses without charging; an unaffordable fee
 * refuses without handing off; the happy path hands off with cause {@link WorldTeleportCause#SPAWN} and charges
 * exactly once only when the teleport is accepted; a bypass holder is exempt from the fee; and {@code forced}
 * hands off with cause {@link WorldTeleportCause#ADMIN} ignoring both restriction and fee. The teleporter, fee,
 * permissions, engine, publisher, notifier and scheduler are all in-memory fakes so the orchestration is
 * asserted without Bukkit.
 */
class WorldTeleportServiceTest {

    private static final PlayerRef PLAYER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef STAFF = new PlayerRef(UUID.randomUUID(), "Op");
    private static final WorldName WORLD = WorldName.of("vip");
    private static final Position SPAWN = new Position(new WorldRef(UUID.randomUUID(), "vip"), 8.5, 64, 8.5, 0f, 0f);

    private final FakeWorldRepository repository = new FakeWorldRepository();
    private final FakeWorldEngine engine = new FakeWorldEngine();
    private final FakeWorldTeleporter teleporter = new FakeWorldTeleporter();
    private final FakeWorldEntryFee entryFee = new FakeWorldEntryFee();
    private final FakePermissions permissions = new FakePermissions();
    private final List<DomainEvent> events = new ArrayList<>();
    private final CapturingNotifier notifier = new CapturingNotifier();

    private WorldTeleportService service() {
        WorldAccessPolicy policy = new WorldAccessPolicy(permissions, engine);
        return new WorldTeleportService(
                repository,
                engine,
                policy,
                teleporter,
                entryFee,
                permissions,
                events::add,
                notifier.notifier(),
                TestSupport.inlineScheduler());
    }

    private ManagedWorld world(boolean restricted, int limit, BigDecimal fee) {
        ManagedWorld base = ManagedWorld.created(WORLD, WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH);
        ManagedWorld configured = base.withSettings(base.settings()
                .with(WorldProperties.ACCESS_RESTRICTED, restricted)
                .with(WorldProperties.PLAYER_LIMIT, limit)
                .with(WorldProperties.ENTRY_FEE, fee));
        repository.save(configured);
        return configured;
    }

    @Test
    void notFoundWhenWorldIsUnknownNeverHandsOffOrCharges() {
        var result = service().spawn(PLAYER, WORLD);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(WorldError.NOT_FOUND);
        assertThat(teleporter.handoffs).isEmpty();
        assertThat(entryFee.charges).isEmpty();
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_NOT_FOUND);
    }

    @Test
    void loadsOnDemandWhenTheWorldIsNotYetLoadedThenHandsOff() {
        world(false, 0, BigDecimal.ZERO);
        engine.spawnPoint = Optional.of(SPAWN);
        // the world is registered but not in the engine's loaded set

        var result = service().spawn(PLAYER, WORLD);

        assertThat(result.isOk()).isTrue();
        assertThat(engine.lastLoaded).isNotNull().extracting(ManagedWorld::name).isEqualTo(WORLD);
        assertThat(teleporter.handoffs).hasSize(1);
        assertThat(teleporter.handoffs.get(0).cause()).isEqualTo(WorldTeleportCause.SPAWN);
    }

    @Test
    void deniesPermissionPublishesEventAndNeitherHandsOffNorCharges() {
        world(true, 0, BigDecimal.ZERO);
        engine.loaded.add(WORLD.value());
        engine.spawnPoint = Optional.of(SPAWN);

        var result = service().spawn(PLAYER, WORLD);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(WorldError.ACCESS_DENIED);
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(WorldEntryDenied.class);
        assertThat(((WorldEntryDenied) events.get(0)).reason()).isEqualTo(AccessDecision.DENIED_PERMISSION);
        assertThat(teleporter.handoffs).isEmpty();
        assertThat(entryFee.charges).isEmpty();
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_ENTER_DENIED_PERMISSION);
    }

    @Test
    void deniesFullPublishesEventAndDoesNotHandOff() {
        world(false, 2, BigDecimal.ZERO);
        engine.loaded.add(WORLD.value());
        engine.playerCount = 2;
        engine.spawnPoint = Optional.of(SPAWN);

        var result = service().spawn(PLAYER, WORLD);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(WorldError.ACCESS_DENIED);
        assertThat(events).hasSize(1);
        assertThat(((WorldEntryDenied) events.get(0)).reason()).isEqualTo(AccessDecision.DENIED_FULL);
        assertThat(teleporter.handoffs).isEmpty();
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_ENTER_DENIED_FULL);
    }

    @Test
    void insufficientFundsNeitherHandsOffNorCharges() {
        world(false, 0, new BigDecimal("50"));
        engine.loaded.add(WORLD.value());
        engine.spawnPoint = Optional.of(SPAWN);
        entryFee.canAfford = false;

        var result = service().spawn(PLAYER, WORLD);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(WorldError.ENTRY_FEE_UNAFFORDABLE);
        assertThat(teleporter.handoffs).isEmpty();
        assertThat(entryFee.charges).isEmpty();
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_ENTER_FEE_INSUFFICIENT);
    }

    @Test
    void happyPathWithFeeHandsOffChargesOnceAndNotifies() {
        world(false, 0, new BigDecimal("50"));
        engine.loaded.add(WORLD.value());
        engine.spawnPoint = Optional.of(SPAWN);
        teleporter.accepted = true;

        var result = service().spawn(PLAYER, WORLD);

        assertThat(result.isOk()).isTrue();
        assertThat(teleporter.handoffs).hasSize(1);
        assertThat(teleporter.handoffs.get(0).who()).isEqualTo(PLAYER);
        assertThat(teleporter.handoffs.get(0).cause()).isEqualTo(WorldTeleportCause.SPAWN);
        assertThat(entryFee.charges).containsExactly(new BigDecimal("50"));
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_ENTER_FEE_CHARGED);
    }

    @Test
    void teleportRejectedWithFeeAttemptsHandOffButDoesNotCharge() {
        world(false, 0, new BigDecimal("50"));
        engine.loaded.add(WORLD.value());
        engine.spawnPoint = Optional.of(SPAWN);
        teleporter.accepted = false;

        var result = service().spawn(PLAYER, WORLD);

        assertThat(result.isOk()).isTrue();
        assertThat(teleporter.handoffs).hasSize(1);
        assertThat(entryFee.charges).isEmpty();
        assertThat(notifier.keys).isEmpty();
    }

    @Test
    void bypassHolderWithFeeHandsOffButIsExemptFromTheCharge() {
        world(false, 0, new BigDecimal("50"));
        engine.loaded.add(WORLD.value());
        engine.spawnPoint = Optional.of(SPAWN);
        teleporter.accepted = true;
        permissions.grant(PLAYER, WorldAccessPolicy.BYPASS_NODE);

        var result = service().spawn(PLAYER, WORLD);

        assertThat(result.isOk()).isTrue();
        assertThat(teleporter.handoffs).hasSize(1);
        assertThat(entryFee.charges).isEmpty();
    }

    @Test
    void forcedHandsOffWithAdminCauseIgnoringRestrictionAndFee() {
        world(true, 1, new BigDecimal("50"));
        engine.loaded.add(WORLD.value());
        engine.playerCount = 1;
        engine.spawnPoint = Optional.of(SPAWN);
        teleporter.accepted = true;

        var result = service().forced(STAFF, PLAYER, WORLD);

        assertThat(result.isOk()).isTrue();
        assertThat(events).isEmpty();
        assertThat(entryFee.charges).isEmpty();
        assertThat(teleporter.handoffs).hasSize(1);
        assertThat(teleporter.handoffs.get(0).who()).isEqualTo(PLAYER);
        assertThat(teleporter.handoffs.get(0).cause()).isEqualTo(WorldTeleportCause.ADMIN);
        assertThat(notifier.keys).containsExactly(WorldsMessageKey.WORLD_TP_OTHER);
    }

    @Test
    void forcedOnSelfDoesNotNotifyTheOtherKey() {
        world(false, 0, BigDecimal.ZERO);
        engine.loaded.add(WORLD.value());
        engine.spawnPoint = Optional.of(SPAWN);
        teleporter.accepted = true;

        var result = service().forced(STAFF, STAFF, WORLD);

        assertThat(result.isOk()).isTrue();
        assertThat(teleporter.handoffs).hasSize(1);
        assertThat(notifier.keys).isEmpty();
    }

    private record Handoff(PlayerRef who, Position to, WorldTeleportCause cause) {}

    /** Records every hand-off and replies with a settable acceptance flag. */
    private static final class FakeWorldTeleporter implements WorldTeleporter {
        final List<Handoff> handoffs = new ArrayList<>();
        boolean accepted = true;

        @Override
        public boolean teleport(PlayerRef who, Position to, WorldTeleportCause cause) {
            handoffs.add(new Handoff(who, to, cause));
            return accepted;
        }
    }

    /** Records every charge and answers affordability from a settable flag. */
    private static final class FakeWorldEntryFee implements WorldEntryFee {
        final List<BigDecimal> charges = new ArrayList<>();
        boolean canAfford = true;

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount) {
            return canAfford;
        }

        @Override
        public boolean charge(PlayerRef who, BigDecimal amount) {
            charges.add(amount);
            return true;
        }
    }

    /** A {@code Permissions} granting only an explicit per-player set of plain nodes; quotas are never resolved. */
    private static final class FakePermissions implements Permissions {
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
            throw new UnsupportedOperationException("the teleport service never resolves a quota");
        }
    }

    /** Captures the message keys the service emits, in order, through a real {@link WorldNotifier}. */
    private static final class CapturingNotifier {
        final List<MessageKey> keys = new ArrayList<>();

        WorldNotifier notifier() {
            MessageSink sink = (v, text) -> {};
            return new WorldNotifier(new RecordingMessages(keys), sink);
        }
    }

    /** A {@link Messages} that records each resolved key and renders it as its raw key string. */
    private record RecordingMessages(List<MessageKey> seen) implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            seen.add(key);
            return key.key();
        }
    }
}
