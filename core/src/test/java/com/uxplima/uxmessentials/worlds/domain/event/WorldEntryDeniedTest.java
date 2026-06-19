package com.uxplima.uxmessentials.worlds.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.domain.AccessDecision;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.junit.jupiter.api.Test;

class WorldEntryDeniedTest {

    private static final WorldName NAME = WorldName.of("creative");
    private static final PlayerRef PLAYER = new PlayerRef(UUID.randomUUID(), "Steve");

    @Test
    void exposesNamePlayerAndReason() {
        WorldEntryDenied event = new WorldEntryDenied(NAME, PLAYER, AccessDecision.DENIED_PERMISSION);
        assertThat(event.name()).isEqualTo(NAME);
        assertThat(event.player()).isEqualTo(PLAYER);
        assertThat(event.reason()).isEqualTo(AccessDecision.DENIED_PERMISSION);
    }

    @Test
    void isAWorldEvent() {
        WorldEntryDenied event = new WorldEntryDenied(NAME, PLAYER, AccessDecision.DENIED_FULL);
        assertThat(event).isInstanceOf(WorldEvent.class);
    }

    @Test
    void rejectsAnAllowedReason() {
        assertThatThrownBy(() -> new WorldEntryDenied(NAME, PLAYER, AccessDecision.ALLOWED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("denial reason must not be ALLOWED");
    }

    @SuppressWarnings("NullAway") // deliberately feeds null to verify the constructor rejects it at runtime
    @Test
    void rejectsNullArguments() {
        assertThatNullPointerException()
                .isThrownBy(() -> new WorldEntryDenied(null, PLAYER, AccessDecision.DENIED_FULL));
        assertThatNullPointerException().isThrownBy(() -> new WorldEntryDenied(NAME, null, AccessDecision.DENIED_FULL));
        assertThatNullPointerException().isThrownBy(() -> new WorldEntryDenied(NAME, PLAYER, null));
    }
}
