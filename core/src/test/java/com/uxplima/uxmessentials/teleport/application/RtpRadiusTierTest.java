package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The per-permission RTP radius tier ({@code uxmessentials.rtp.radius.<n>}), resolved through the shared
 * {@link Permissions} quota reducer. A player with no matching tier searches the area's own configured maximum;
 * a higher tier raises the effective outer radius (highest wins), and the family it resolves is the MAX-reduced
 * quota family every value-bearing node uses. The reducer itself (higher tier wins across groups) is the shared
 * port's concern; here we pin that RTP asks it the right family with the area's max as the config default and
 * applies the answer to the search area.
 */
class RtpRadiusTierTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Explorer");
    private static final SafeSearchArea BASE = new SafeSearchArea(WORLD, 0, 0, 100, 5_000, 30_000);

    @Test
    void resolvesTheRadiusFamilyWithTheAreaMaximumAsTheConfigDefault() {
        RecordingPermissions permissions = new RecordingPermissions(5_000);

        new RtpRadiusTier(permissions).clamp(WHO, BASE);

        assertThat(permissions.lastFamily).isEqualTo(RtpRadiusTier.FAMILY);
        assertThat(RtpRadiusTier.FAMILY.direction()).isEqualTo(Permissions.QuotaReduction.MAX);
        assertThat(permissions.lastDefault).isEqualTo(5_000L); // the area's configured maximum is the fallback
    }

    @Test
    void aHigherTierRaisesTheEffectiveMaxRadius() {
        SafeSearchArea clamped = new RtpRadiusTier(new RecordingPermissions(12_000)).clamp(WHO, BASE);

        assertThat(clamped.configuredMaxRadius()).isEqualTo(12_000.0);
    }

    @Test
    void anAbsentTierLeavesTheConfiguredMaximumUntouched() {
        // resolveQuota folds in the config default (the area max), so a player with no node resolves back to it.
        SafeSearchArea clamped = new RtpRadiusTier(new RecordingPermissions(5_000)).clamp(WHO, BASE);

        assertThat(clamped.configuredMaxRadius()).isEqualTo(5_000.0);
    }

    @Test
    void aResolvedRadiusBelowTheInnerRingIsFlooredAtIt() {
        // A pathological tier below the min radius still yields a legal area (max >= min); the border clamps on serve.
        SafeSearchArea clamped = new RtpRadiusTier(new RecordingPermissions(40)).clamp(WHO, BASE);

        assertThat(clamped.configuredMaxRadius()).isEqualTo(100.0); // floored at minRadius
    }

    /** Returns a preset resolved radius and records the family and default it was asked for. */
    private static final class RecordingPermissions implements Permissions {
        private final long resolved;
        private @Nullable QuotaFamily lastFamily;
        private long lastDefault;

        RecordingPermissions(long resolved) {
            this.resolved = resolved;
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            this.lastFamily = family;
            this.lastDefault = configDefault;
            return QuotaResult.limited(resolved);
        }
    }
}
