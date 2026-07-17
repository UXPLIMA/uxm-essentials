package com.uxplima.uxmessentials.vanish.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Pairs the {@link Messages} resolution port with the {@link MessageSink} delivery port so a vanish use case sends a
 * {@link MessageKey} to a viewer in one call — the {@code /vanish} on/off confirmation. Resolution happens in the
 * viewer's locale and delivery hops to the viewer's region thread, silently no-opping when they are offline. Mirrors
 * the presence context's {@code PresenceNotifier} rather than sharing one.
 */
public final class VanishNotifier {

    private final Messages messages;
    private final MessageSink sink;

    public VanishNotifier(Messages messages, MessageSink sink) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** Resolve {@code key} for {@code viewer} and deliver it. */
    public void send(PlayerRef viewer, MessageKey key) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        sink.deliver(viewer, messages.resolve(viewer, key, Map.of()));
    }
}
