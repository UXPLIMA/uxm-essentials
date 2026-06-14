package com.uxplima.uxmessentials.staff.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.application.port.StaffAudience;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.EchoMessages;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.StaffKeySink;
import com.uxplima.uxmessentials.staff.adapter.outbound.MessagingStaffChannel;
import com.uxplima.uxmessentials.staff.application.SendStaffChat;
import com.uxplima.uxmessentials.staff.application.port.StaffChannel;
import com.uxplima.uxmessentials.staff.domain.event.StaffChatSent;
import org.junit.jupiter.api.Test;

/**
 * The messaging-backed staff chat: {@link MessagingStaffChannel} resolves the staff audience through the
 * {@link StaffAudience} node and {@link SendStaffChat} fans the formatted line out to exactly that audience,
 * publishing the audit event. The {@code NONE} binding degrades to silence with an empty audience.
 */
class MessagingStaffChannelTest {

    private static final String NODE = "uxmessentials.staff.chat";

    @Test
    void aStaffChatReachesEveryStaffChatNodeHolder() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        PlayerRef bob = new PlayerRef(UUID.randomUUID(), "Bob");
        StaffKeySink sink = new StaffKeySink();
        StaffChannel channel =
                new MessagingStaffChannel(fixedAudience(List.of(alice, bob)), new EchoMessages(), sink, NODE);
        StaffAdapterFakes.RecordingEvents events = new StaffAdapterFakes.RecordingEvents();

        new SendStaffChat(channel, events).send(alice, "patrolling spawn");

        // Both staff-chat holders received the formatted line (player + message folded into the format key).
        assertThat(sink.delivered).hasSize(2);
        assertThat(sink.delivered).allMatch(line -> line.contains("Alice") && line.contains("patrolling spawn"));
        assertThat(events.published).hasExactlyElementsOfTypes(StaffChatSent.class);
    }

    @Test
    void theNoneChannelDegradesToAnEmptyAudienceAndNoDelivery() {
        StaffKeySink sink = new StaffKeySink();
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        StaffAdapterFakes.RecordingEvents events = new StaffAdapterFakes.RecordingEvents();

        // With messaging off the wiring binds StaffChannel.NONE; the send must not throw and must deliver nothing.
        new SendStaffChat(StaffChannel.NONE, events).send(alice, "anyone there?");

        assertThat(sink.delivered).isEmpty();
        assertThat(events.published).hasExactlyElementsOfTypes(StaffChatSent.class);
    }

    private static StaffAudience fixedAudience(List<PlayerRef> audience) {
        return node -> NODE.equals(node) ? audience : List.of();
    }
}
