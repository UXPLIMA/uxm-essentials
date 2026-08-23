package com.uxplima.uxmessentials.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class PlayerRefTest {

    @Test
    void systemIdentityDoesNotCollideWithLegacyZeroUuidSentinels() {
        PlayerRef system = PlayerRef.system("console");
        PlayerRef legacySentinel = new PlayerRef(new UUID(0L, 0L), "gui-internal");

        assertThat(system.isSystem()).isTrue();
        assertThat(legacySentinel.isSystem()).isFalse();
        assertThat(system).isNotEqualTo(legacySentinel);
    }
}
