package com.uxplima.uxmessentials.messaging.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Pairs the {@link Messages} resolution port with the {@link MessageSink} delivery port so a messaging use
 * case sends a {@link MessageKey} to a viewer in one call — the context's plain feedback channel (a
 * rejection reason, a toggle confirmation). The richer message/mail/spy shapes go through the dedicated
 * {@code MessageDelivery} port; this notifier covers the single-line feedback the use cases emit. Resolution
 * happens in the viewer's locale and delivery hops to the viewer's region thread, silently no-opping when
 * they are offline. Mirrors the homes context's {@code HomeNotifier} rather than sharing one.
 */
public final class MessagingNotifier {

    private final Messages messages;
    private final MessageSink sink;

    public MessagingNotifier(Messages messages, MessageSink sink) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** Resolve {@code key} for {@code viewer} with {@code placeholders} and deliver it. */
    public void send(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        sink.deliver(viewer, messages.resolve(viewer, key, placeholders));
    }

    /** Resolve and deliver {@code key} with no placeholders. */
    public void send(PlayerRef viewer, MessageKey key) {
        send(viewer, key, Map.of());
    }
}
