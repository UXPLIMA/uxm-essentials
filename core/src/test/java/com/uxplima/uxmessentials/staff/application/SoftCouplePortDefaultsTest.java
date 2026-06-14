package com.uxplima.uxmessentials.staff.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffChannel;
import com.uxplima.uxmessentials.staff.application.port.StaffFreeze;
import com.uxplima.uxmessentials.staff.application.port.StaffInspector;
import com.uxplima.uxmessentials.staff.application.port.StaffTeleport;
import com.uxplima.uxmessentials.staff.application.port.StaffVanish;
import org.junit.jupiter.api.Test;

class SoftCouplePortDefaultsTest {

    private static final PlayerRef A = new PlayerRef(UUID.randomUUID(), "A");
    private static final PlayerRef B = new PlayerRef(UUID.randomUUID(), "B");

    @Test
    void vanishNoneIsANoOp() {
        // The binding when presence is disabled: setting vanish does nothing and never throws.
        assertThatCode(() -> {
                    StaffVanish.NONE.setVanished(A, true);
                    StaffVanish.NONE.setVanished(A, false);
                })
                .doesNotThrowAnyException();
    }

    @Test
    void inspectorNoneIsANoOp() {
        // The binding when playerstate is disabled: inspecting does nothing and never throws.
        assertThatCode(() -> StaffInspector.NONE.inspect(A, B)).doesNotThrowAnyException();
    }

    @Test
    void channelNoneHasNoAudienceAndSendIsANoOp() {
        // The binding when messaging is disabled: empty audience, send degrades to silence.
        assertThat(StaffChannel.NONE.onlineStaff()).isEmpty();
        assertThatCode(() -> StaffChannel.NONE.send(A, List.of(B), "hello")).doesNotThrowAnyException();
    }

    @Test
    void freezeNoneReportsUnavailableAndNeverFrozen() {
        // The binding when moderation is disabled: the freeze gadget degrades to unavailable.
        assertThat(StaffFreeze.NONE.toggle(A, B)).isEqualTo(StaffFreeze.FreezeOutcome.UNAVAILABLE);
        assertThat(StaffFreeze.NONE.isFrozen(B)).isFalse();
    }

    @Test
    void teleportNoneCannotStartTheTeleport() {
        // The binding when teleport is disabled: the compass gadget cannot move the staff member.
        assertThat(StaffTeleport.NONE.teleportTo(A, B)).isFalse();
    }
}
