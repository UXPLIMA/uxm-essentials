package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorldPropertyStringTest {

    @Test
    void ofStringDecodesAWorldName() {
        assertThat(WorldProperties.PORTAL_NETHER_LINK.decode("world_nether")).contains("world_nether");
    }

    @Test
    void ofStringRejectsBlankOrWhitespace() {
        assertThat(WorldProperties.PORTAL_NETHER_LINK.decode("")).isEmpty();
        assertThat(WorldProperties.PORTAL_NETHER_LINK.decode("   ")).isEmpty();
    }

    @Test
    void ofStringDefaultsToEmpty() {
        assertThat(WorldProperties.PORTAL_NETHER_LINK.defaultValue()).isEmpty();
        assertThat(WorldProperties.PORTAL_END_LINK.defaultValue()).isEmpty();
    }

    @Test
    void ofStringEncodeRoundTrips() {
        String decoded = WorldProperties.PORTAL_END_LINK.decode("the_end").orElseThrow();
        assertThat(WorldProperties.PORTAL_END_LINK.encode(decoded)).isEqualTo("the_end");
    }

    @Test
    void byKeyResolvesBothPortalLinks() {
        assertThat(WorldProperties.byKey("portal-nether-link")).containsSame(WorldProperties.PORTAL_NETHER_LINK);
        assertThat(WorldProperties.byKey("portal-end-link")).containsSame(WorldProperties.PORTAL_END_LINK);
    }

    @Test
    void allContainsBothPortalLinks() {
        assertThat(WorldProperties.ALL).contains(WorldProperties.PORTAL_NETHER_LINK, WorldProperties.PORTAL_END_LINK);
    }

    @Test
    void portalKindHasExactlyNetherAndEnd() {
        assertThat(PortalKind.values()).containsExactly(PortalKind.NETHER, PortalKind.END);
    }
}
