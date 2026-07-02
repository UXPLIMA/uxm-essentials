package com.uxplima.uxmessentials.poses.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.poses.application.port.PlayerSitPreferences;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link TogglePlayerSit}: a flip inverts the player's "allow being sat on" preference and reports the new
 * state so the command can render the matching message. Starting from the GSit default (allows), the first flip
 * refuses and the second allows again.
 */
class TogglePlayerSitTest {

    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Steve");

    private final FakePlayerSitPreferences preferences = new FakePlayerSitPreferences();
    private final TogglePlayerSit togglePlayerSit = new TogglePlayerSit(preferences);

    @Test
    void firstFlipRefusesThenSecondFlipAllowsAgain() {
        // The default is allow, so the first flip turns refusal on.
        assertThat(togglePlayerSit.toggle(WHO)).isFalse();
        assertThat(preferences.allowsSitting(WHO)).isFalse();

        assertThat(togglePlayerSit.toggle(WHO)).isTrue();
        assertThat(preferences.allowsSitting(WHO)).isTrue();
    }

    /** A per-player preference map; an unset player allows sitting (the GSit default). */
    private static final class FakePlayerSitPreferences implements PlayerSitPreferences {
        private final Map<UUID, Boolean> allowing = new HashMap<>();

        @Override
        public boolean allowsSitting(PlayerRef who) {
            return allowing.getOrDefault(who.uuid(), true);
        }

        @Override
        public boolean toggle(PlayerRef who) {
            boolean now = !allowsSitting(who);
            allowing.put(who.uuid(), now);
            return now;
        }
    }
}
