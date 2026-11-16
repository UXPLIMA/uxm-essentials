package com.uxplima.uxmessentials.teleport.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/** A staff teleport stops at a player's {@code /tptoggle}; only the override verbs pass it. */
class StaffTeleportGateTest {

    private final TeleportFakes.Flags flags = new TeleportFakes.Flags();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final StaffTeleportGate gate = new StaffTeleportGate(flags, notifier.notifier());

    @Test
    void aPlayerWhoRefusesTeleportsStopsAPlainTpAndTheActorIsPointedToTheOverride() {
        flags.setAcceptsRequests(SAM, false);

        assertThat(gate.admits(ALEX, SAM, false)).isFalse();

        assertThat(notifier.sent())
                .containsExactly(new RecordingNotifier.Sent(ALEX, "teleport.tp.toggled-off", Map.of("player", "Sam")));
    }

    @Test
    void theOverrideVerbsReachAPlayerWhoRefusesTeleports() {
        flags.setAcceptsRequests(SAM, false);

        assertThat(gate.admits(ALEX, SAM, true)).isTrue();

        assertThat(notifier.sent()).isEmpty();
    }

    @Test
    void aPlayerWhoAcceptsTeleportsIsReachedByAPlainTp() {
        assertThat(gate.admits(ALEX, SAM, false)).isTrue();
    }
}
