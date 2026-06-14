package com.uxplima.uxmessentials.staff.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffChannel;
import com.uxplima.uxmessentials.staff.application.port.StaffInspector;
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
}
