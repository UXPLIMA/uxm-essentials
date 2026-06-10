package com.uxplima.uxmessentials.kits.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit coverage of {@link KitFullPolicy#parse}: the two tokens parse case- and space-insensitively, and a
 * blank, null, or unrecognised value defaults to {@link KitFullPolicy#DROP} so a kit never silently becomes a
 * refusing one through a typo. The default also keeps the long-standing overflow-drop behaviour for every
 * existing kit that names no policy at all.
 */
class KitFullPolicyTest {

    @Test
    void parsesBothTokensToleratingCaseAndSpaces() {
        assertThat(KitFullPolicy.parse("drop")).isEqualTo(KitFullPolicy.DROP);
        assertThat(KitFullPolicy.parse("deny")).isEqualTo(KitFullPolicy.DENY);
        assertThat(KitFullPolicy.parse("  DENY ")).isEqualTo(KitFullPolicy.DENY);
        assertThat(KitFullPolicy.parse("Drop")).isEqualTo(KitFullPolicy.DROP);
    }

    @Test
    void defaultsToDropForNullBlankOrUnknown() {
        assertThat(KitFullPolicy.parse(null)).isEqualTo(KitFullPolicy.DROP);
        assertThat(KitFullPolicy.parse("")).isEqualTo(KitFullPolicy.DROP);
        assertThat(KitFullPolicy.parse("keep")).isEqualTo(KitFullPolicy.DROP);
    }

    @Test
    void tokenIsTheLowercaseName() {
        assertThat(KitFullPolicy.DROP.token()).isEqualTo("drop");
        assertThat(KitFullPolicy.DENY.token()).isEqualTo("deny");
    }

    @Test
    void aKitDefaultsToDropAndCanSwitchToDeny() {
        KitDefinition kit = KitDefinition.repeatable(
                KitId.of("loadout"), java.util.List.of(KitItem.of("payload", 1)), java.time.Duration.ZERO);

        assertThat(kit.onFull()).isEqualTo(KitFullPolicy.DROP);
        assertThat(kit.withOnFull(KitFullPolicy.DENY).onFull()).isEqualTo(KitFullPolicy.DENY);
    }
}
