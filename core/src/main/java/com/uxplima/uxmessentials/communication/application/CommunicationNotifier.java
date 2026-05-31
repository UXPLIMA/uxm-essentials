package com.uxplima.uxmessentials.communication.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Pairs the {@link Messages} resolution port with the {@link MessageSink} delivery port so a communication use
 * case sends one of the plugin's own {@link MessageKey} strings to a viewer in one call — the
 * {@code /broadcasttoggle} confirmations, the missing-info-page error, the announcer-reloaded notice. Resolution
 * happens in the viewer's locale and delivery hops to the viewer's region thread, silently no-opping when they
 * are offline.
 *
 * <p>This notifier carries only {@code MessageKey} content — the plugin's own strings. The operator-authored
 * join/quit/death templates, announcer lines, and info-page bodies do <em>not</em> flow through here: they are
 * rendered straight from their raw MiniMessage by the adapter, keeping the parity-checked keys and the unchecked
 * operator content cleanly apart. Mirrors the presence context's {@code PresenceNotifier} rather than sharing one.
 */
public final class CommunicationNotifier {

    private final Messages messages;
    private final MessageSink sink;

    public CommunicationNotifier(Messages messages, MessageSink sink) {
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
