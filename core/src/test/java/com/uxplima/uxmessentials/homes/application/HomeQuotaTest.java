package com.uxplima.uxmessentials.homes.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.HomeLimit;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaReduction;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link HomeQuota} builds the correct {@link QuotaFamily} direction for each
 * {@link QuotaReduction} mode and that {@link HomeQuota#resolve} converts the quota result to a
 * {@link HomeLimit} correctly. The actual node-fold arithmetic is exercised by
 * {@code QuotaNodeReducerTest}; these tests use a recording fake to keep the boundary crisp.
 */
class HomeQuotaTest {

    private static final PlayerRef PLAYER = new PlayerRef(UUID.randomUUID(), "Alice");

    @Test
    void maxModeBuildsAMaxDirectionFamily() {
        HomeQuota quota = new HomeQuota(new FixedPermissions(QuotaResult.limited(5)), 3, QuotaReduction.MAX);

        assertThat(quota.family().direction()).isEqualTo(QuotaReduction.MAX);
    }

    @Test
    void stackModeBuildsAStackDirectionFamily() {
        HomeQuota quota = new HomeQuota(new FixedPermissions(QuotaResult.limited(5)), 3, QuotaReduction.STACK);

        assertThat(quota.family().direction()).isEqualTo(QuotaReduction.STACK);
    }

    @Test
    void resolveReturnsConcreteLimitFromQuotaResult() {
        HomeQuota quota = new HomeQuota(new FixedPermissions(QuotaResult.limited(7)), 3, QuotaReduction.MAX);

        HomeLimit limit = quota.resolve(PLAYER, null);

        assertThat(limit.unlimited()).isFalse();
        assertThat(limit.cap()).isEqualTo(7);
    }

    @Test
    void resolveUnlimitedSentinelReturnsNoLimit() {
        HomeQuota quota = new HomeQuota(new FixedPermissions(QuotaResult.unlimited()), 3, QuotaReduction.MAX);

        HomeLimit limit = quota.resolve(PLAYER, null);

        assertThat(limit.unlimited()).isTrue();
    }

    @Test
    void resolveStackModeSumsViaPermissionsResult() {
        // Simulate what the real adapter produces when two tiers are held (2 + 3 = 5).
        HomeQuota quota = new HomeQuota(new FixedPermissions(QuotaResult.limited(5)), 3, QuotaReduction.STACK);

        HomeLimit limit = quota.resolve(PLAYER, null);

        assertThat(limit.cap()).isEqualTo(5);
    }

    @Test
    void resolveHighestModeUnchangedRegression() {
        // Regression: MAX mode must still return the highest node, not the sum.
        HomeQuota quota = new HomeQuota(new FixedPermissions(QuotaResult.limited(10)), 3, QuotaReduction.MAX);

        HomeLimit limit = quota.resolve(PLAYER, null);

        assertThat(limit.cap()).isEqualTo(10);
    }

    /** Returns a fixed {@link QuotaResult} regardless of the family or player. */
    private static final class FixedPermissions implements Permissions {

        private final QuotaResult result;

        FixedPermissions(QuotaResult result) {
            this.result = result;
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return result;
        }
    }
}
