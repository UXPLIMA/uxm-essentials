package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.worlds.domain.AccessDecision;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * {@code WorldAccessPolicy} is the pure entry-gate decision shared by the {@code /world} enter command and
 * the cross-world teleport listener. The truth table: a bypass holder is always {@link AccessDecision#ALLOWED}
 * (even when the world is both restricted and full); an unrestricted, unbounded world is open to everyone; a
 * restricted world denies with {@link AccessDecision#DENIED_PERMISSION} unless the player holds the world's
 * enter node; and a world at or over its positive player limit denies with {@link AccessDecision#DENIED_FULL}
 * while a zero limit means unlimited. Permissions and the engine are in-memory fakes so the decision is
 * asserted without Bukkit.
 */
class WorldAccessPolicyTest {

    private static final PlayerRef PLAYER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final WorldName WORLD = WorldName.of("vip");

    private static ManagedWorld world(boolean restricted, int limit) {
        ManagedWorld base = ManagedWorld.created(WORLD, WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH);
        return base.withSettings(base.settings()
                .with(WorldProperties.ACCESS_RESTRICTED, restricted)
                .with(WorldProperties.PLAYER_LIMIT, limit));
    }

    @Test
    void allowsAnUnrestrictedUnboundedWorld() {
        WorldAccessPolicy policy = new WorldAccessPolicy(new FakePermissions(), new FakeWorldEngine());

        assertThat(policy.decide(PLAYER, world(false, 0))).isEqualTo(AccessDecision.ALLOWED);
    }

    @Test
    void deniesPermissionWhenRestrictedAndTheEnterNodeIsMissing() {
        WorldAccessPolicy policy = new WorldAccessPolicy(new FakePermissions(), new FakeWorldEngine());

        assertThat(policy.decide(PLAYER, world(true, 0))).isEqualTo(AccessDecision.DENIED_PERMISSION);
    }

    @Test
    void allowsWhenRestrictedAndThePlayerHoldsTheEnterNode() {
        FakePermissions permissions = new FakePermissions(WorldAccessPolicy.enterNode(WORLD));
        WorldAccessPolicy policy = new WorldAccessPolicy(permissions, new FakeWorldEngine());

        assertThat(policy.decide(PLAYER, world(true, 0))).isEqualTo(AccessDecision.ALLOWED);
    }

    @Test
    void deniesFullWhenTheLimitIsReached() {
        FakeWorldEngine engine = new FakeWorldEngine();
        engine.playerCount = 2;
        WorldAccessPolicy policy = new WorldAccessPolicy(new FakePermissions(), engine);

        assertThat(policy.decide(PLAYER, world(false, 2))).isEqualTo(AccessDecision.DENIED_FULL);
    }

    @Test
    void treatsAZeroLimitAsUnlimitedEvenWithPlayersPresent() {
        FakeWorldEngine engine = new FakeWorldEngine();
        engine.playerCount = 5;
        WorldAccessPolicy policy = new WorldAccessPolicy(new FakePermissions(), engine);

        assertThat(policy.decide(PLAYER, world(false, 0))).isEqualTo(AccessDecision.ALLOWED);
    }

    @Test
    void bypassHolderEntersEvenWhenRestrictedAndFull() {
        FakePermissions permissions = new FakePermissions(WorldAccessPolicy.BYPASS_NODE);
        FakeWorldEngine engine = new FakeWorldEngine();
        engine.playerCount = 2;
        WorldAccessPolicy policy = new WorldAccessPolicy(permissions, engine);

        assertThat(policy.decide(PLAYER, world(true, 2))).isEqualTo(AccessDecision.ALLOWED);
    }

    /** A {@code Permissions} that grants only an explicit set of plain nodes; quota resolution is never exercised. */
    private static final class FakePermissions implements Permissions {
        private final Set<String> granted = new HashSet<>();

        FakePermissions(String... nodes) {
            for (String node : nodes) {
                granted.add(node);
            }
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted.contains(node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            throw new UnsupportedOperationException("the access policy never resolves a quota");
        }
    }
}
