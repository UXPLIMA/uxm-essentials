package com.uxplima.uxmessentials.staff.adapter.outbound;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.StaffAudience;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * Broadcasts the staff-roster enter/exit alerts: when a staff member toggles staff mode, every other online
 * holder of the staff-chat audience node hears about it. It resolves the audience through the messaging
 * context's {@link StaffAudience} and fans each line out through the shared {@link Messages}/{@link MessageSink}
 * pair, the same staff-audience shape {@code MessagingStaffChannel} uses for staff chat.
 *
 * <p>The toggling player is skipped (they already get their own {@code STAFF_MODE_ON}/{@code OFF} feedback), so
 * the alert is the rest of the roster learning of the change. Bound only when the messaging module is enabled;
 * with messaging off the subscription is never wired, so a toggle simply produces no roster alert.
 */
@NullMarked
public final class MessagingStaffAlerts {

    private final StaffAudience audience;
    private final MessageSink sink;
    private final Messages messages;
    private final String audienceNode;

    public MessagingStaffAlerts(StaffAudience audience, MessageSink sink, Messages messages, String audienceNode) {
        this.audience = Objects.requireNonNull(audience, "audience");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.audienceNode = Objects.requireNonNull(audienceNode, "audienceNode");
    }

    /** Tell every online staff member except {@code who} that {@code who} entered staff mode. */
    public void announceEnter(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        broadcast(who, StaffMessageKey.STAFF_ALERT_ENTER);
    }

    /** Tell every online staff member except {@code who} that {@code who} left staff mode. */
    public void announceExit(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        broadcast(who, StaffMessageKey.STAFF_ALERT_EXIT);
    }

    private void broadcast(PlayerRef who, StaffMessageKey key) {
        Map<String, String> placeholders = Map.of("player", who.name());
        for (PlayerRef recipient : audience.onlineWith(audienceNode)) {
            if (recipient.uuid().equals(who.uuid())) {
                continue;
            }
            sink.deliver(recipient, messages.resolve(recipient, key, placeholders));
        }
    }
}
