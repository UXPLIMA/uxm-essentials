package com.uxplima.uxmessentials.staff.adapter.outbound;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.messaging.application.port.StaffAudience;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.RemoteSyncListener;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.StaffActions;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.StaffActionFrame.Action;
import com.uxplima.uxmessentials.staff.domain.StaffMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * Broadcasts the staff-roster enter/exit alerts: when a staff member toggles staff mode, every other online
 * holder of the staff-chat audience node hears about it. It resolves the audience through the messaging
 * context's {@link StaffAudience} and fans each line out through the shared {@link Messages}/{@link MessageSink}
 * pair, the same staff-audience shape {@code MessagingStaffChannel} uses for staff chat.
 *
 * <p>The toggling player is skipped (they already get their own {@code STAFF_MODE_ON}/{@code OFF} feedback), so
 * the alert is the rest of the roster learning of the change. On a network the staff of the other backends hear it
 * too: the alert is sent over the bus and each backend shows it to its own holders of the node. Bound only when the messaging module is enabled;
 * with messaging off the subscription is never wired, so a toggle simply produces no roster alert.
 *
 * <p><b>Threading.</b> Unlike {@code /helpop} and {@code /staffchat} (Brigadier handlers on the global region
 * thread), this alert is driven by the {@code /staffmode} enter/exit domain events, which fire synchronously on
 * the <i>target's entity</i> region thread. Enumerating the roster there would be an off-global cross-region read
 * that tears on Folia, so the audience read and the per-recipient delivery both run inside a single
 * {@code scheduler.onGlobal} hop. The {@link MessageSink} then re-targets each delivery to the recipient's own
 * entity thread, so no thread ever blocks waiting on another: the fan-out is push-shaped, never request-reply.
 */
@NullMarked
public final class MessagingStaffAlerts {

    private final StaffAudience audience;
    private final MessageSink sink;
    private final Messages messages;
    private final Scheduler scheduler;
    /** The alerts a backend shows when another sends them; anything else in a notice frame is not for this one. */
    private static final List<StaffMessageKey> ALERTS =
            List.of(StaffMessageKey.STAFF_ALERT_ENTER, StaffMessageKey.STAFF_ALERT_EXIT);

    private final String audienceNode;
    private final StaffActions network;

    /** @param network carries each alert to the staff on the other backends */
    public MessagingStaffAlerts(
            StaffAudience audience,
            MessageSink sink,
            Messages messages,
            Scheduler scheduler,
            String audienceNode,
            StaffActions network) {
        this.audience = Objects.requireNonNull(audience, "audience");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.audienceNode = Objects.requireNonNull(audienceNode, "audienceNode");
        this.network = Objects.requireNonNull(network, "network");
    }

    /** A listener that shows this backend's staff the alerts another backend sent, while {@code live} holds. */
    public RemoteSyncListener listener(BooleanSupplier live) {
        Objects.requireNonNull(live, "live");
        return StaffActions.listener(
                network.serverId(),
                Action.STAFF_NOTICE,
                frame -> ALERTS.stream()
                        .filter(alert -> alert.key().equals(frame.key()) && live.getAsBoolean())
                        .findFirst()
                        .ifPresent(alert -> fanOut(frame.target(), alert, frame.values())));
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
        fanOut(who.uuid(), key, placeholders);
        network.notice(key.key(), placeholders);
    }

    private void fanOut(UUID who, StaffMessageKey key, Map<String, String> placeholders) {
        scheduler.onGlobal(() -> {
            for (PlayerRef recipient : audience.onlineWith(audienceNode)) {
                if (recipient.uuid().equals(who)) {
                    continue;
                }
                sink.deliver(recipient, messages.resolve(recipient, key, placeholders));
            }
        });
    }
}
