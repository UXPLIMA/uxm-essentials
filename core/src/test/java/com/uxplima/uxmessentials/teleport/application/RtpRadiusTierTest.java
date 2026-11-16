package com.uxplima.uxmessentials.teleport.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.WORLD;
import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import com.uxplima.uxmessentials.testing.FakePermissions;
import org.junit.jupiter.api.Test;

/** A held {@code rtp.radius} tier narrows the configured search ring and never reaches past {@code max-radius}. */
class RtpRadiusTierTest {

    private static final String RADIUS = "uxmessentials.rtp.radius";
    private static final SafeSearchArea CONFIGURED = new SafeSearchArea(WORLD, 0, 0, 100, 5000, 30_000);

    private final FakePermissions permissions = new FakePermissions();
    private final RtpRadiusTier tier = new RtpRadiusTier(permissions);

    @Test
    void withNoTierThePlayerSearchesTheConfiguredRadius() {
        assertThat(tier.clamp(ALEX, CONFIGURED).maxRadius()).isEqualTo(5000);
    }

    @Test
    void aTierBelowTheConfiguredRadiusNarrowsTheSearch() {
        permissions.holdTier(ALEX, RADIUS, 1000);

        assertThat(tier.clamp(ALEX, CONFIGURED).maxRadius()).isEqualTo(1000);
    }

    @Test
    void aTierAboveTheConfiguredRadiusNeverWidensTheSearch() {
        permissions.holdTier(ALEX, RADIUS, 20_000);

        assertThat(tier.clamp(ALEX, CONFIGURED).maxRadius()).isEqualTo(5000);
    }

    @Test
    void theLargestTierHeldWinsWithinTheConfiguredRadius() {
        permissions.holdTier(ALEX, RADIUS, 500).holdTier(ALEX, RADIUS, 2000);

        assertThat(tier.clamp(ALEX, CONFIGURED).maxRadius()).isEqualTo(2000);
    }

    @Test
    void theUnlimitedTierSearchesTheConfiguredRadiusAndNoFurther() {
        permissions.quota(RADIUS, QuotaResult.unlimited());

        assertThat(tier.clamp(ALEX, CONFIGURED).maxRadius()).isEqualTo(5000);
    }

    @Test
    void aTierInsideTheInnerRingSearchesTheInnerRingOnly() {
        permissions.holdTier(ALEX, RADIUS, 10);

        SafeSearchArea narrowed = tier.clamp(ALEX, CONFIGURED);

        assertThat(narrowed.maxRadius()).isEqualTo(100);
        assertThat(narrowed.minRadius()).isEqualTo(100);
    }
}
