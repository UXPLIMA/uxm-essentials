package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A staff action reaches the backend it names intact, and every other backend can tell it is not theirs. */
class StaffActionFrameTest {

    private static final UUID FRAME = new UUID(1L, 2L);
    private static final UUID STEVE = new UUID(3L, 4L);

    @Test
    void anActionCrossesTheWireUnchanged() {
        StaffActionFrame sent = new StaffActionFrame(
                "survival", FRAME, "KICK", "lobby", STEVE, "Steve", "Staff", "", Map.of("reason", "spam"));

        assertThat(NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent))).isEqualTo(sent);
        assertThat(sent.type().wireTag()).isEqualTo((byte) 45);
    }

    @Test
    void aNoticeIsForEveryBackendAndAnActionOnlyForTheOneItNames() {
        StaffActionFrame notice = new StaffActionFrame(
                "survival", FRAME, "STAFF_NOTICE", "", STEVE, "-", "", "security.alts.notify", Map.of());
        StaffActionFrame kick =
                new StaffActionFrame("survival", FRAME, "KICK", "lobby", STEVE, "Steve", "", "", Map.of());

        assertThat(notice.isFor("lobby")).isTrue();
        assertThat(notice.isFor("creative")).isTrue();
        assertThat(kick.isFor("lobby")).isTrue();
        assertThat(kick.isFor("creative")).isFalse();
    }

    @Test
    void anActionThatNamesNoBackendIsForNone() {
        StaffActionFrame kick = new StaffActionFrame("survival", FRAME, "KICK", "", STEVE, "Steve", "", "", Map.of());

        assertThat(kick.isFor("lobby")).isFalse();
    }

    @Test
    void anActionANewerBackendAddedIsKnownToNoOne() {
        assertThat(StaffActionFrame.Action.named("KICK")).contains(StaffActionFrame.Action.KICK);
        assertThat(StaffActionFrame.Action.named("SOMETHING_NEWER")).isEqualTo(Optional.empty());
    }

    @Test
    void aFrameCarriesABoundedNumberOfValues() {
        Map<String, String> many = new HashMap<>();
        for (int i = 0; i <= StaffActionFrame.MAX_VALUES; i++) {
            many.put("v" + i, "x");
        }

        assertThatThrownBy(() -> new StaffActionFrame("survival", FRAME, "KICK", "lobby", STEVE, "Steve", "", "", many))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
