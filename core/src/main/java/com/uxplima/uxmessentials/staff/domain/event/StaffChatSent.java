package com.uxplima.uxmessentials.staff.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A message was sent on the staff channel and fanned out to the online staff audience. Recorded so the
 * audit trail and other plugins can observe staff chatter without the message being a sanction of any kind.
 *
 * @param from the staff member who sent the message
 * @param message the chat body, exactly as sent
 */
public record StaffChatSent(PlayerRef from, String message) implements StaffEvent {

    public StaffChatSent {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(message, "message");
    }
}
