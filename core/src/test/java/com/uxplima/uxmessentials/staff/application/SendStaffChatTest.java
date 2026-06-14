package com.uxplima.uxmessentials.staff.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffChannel;
import com.uxplima.uxmessentials.staff.domain.event.StaffChatSent;
import org.junit.jupiter.api.Test;

class SendStaffChatTest {

    private static final PlayerRef FROM = new PlayerRef(UUID.randomUUID(), "Mod");
    private static final PlayerRef STAFF_A = new PlayerRef(UUID.randomUUID(), "Admin");
    private static final PlayerRef STAFF_B = new PlayerRef(UUID.randomUUID(), "Helper");

    @Test
    void fansOutToTheOnlineStaffAudienceAndPublishesTheEvent() {
        var channel = new StaffTestFakes.RecordingChannel(List.of(STAFF_A, STAFF_B));
        var events = new StaffTestFakes().events;

        new SendStaffChat(channel, events).send(FROM, "patrol the spawn");

        assertThat(channel.sentFrom).isEqualTo(FROM);
        assertThat(channel.sentMessage).isEqualTo("patrol the spawn");
        assertThat(channel.sentTo).containsExactly(STAFF_A, STAFF_B);
        assertThat(events.published).containsExactly(new StaffChatSent(FROM, "patrol the spawn"));
    }

    @Test
    void degradesToSilenceWhenTheChannelIsTheNoneNoOp() {
        var events = new StaffTestFakes().events;

        // Still publishes the event for the audit trail even with no messaging module behind it.
        new SendStaffChat(StaffChannel.NONE, events).send(FROM, "anyone around?");

        assertThat(StaffChannel.NONE.onlineStaff()).isEmpty();
        assertThat(events.published).containsExactly(new StaffChatSent(FROM, "anyone around?"));
    }
}
