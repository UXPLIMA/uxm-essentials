package com.uxplima.uxmessentials.shared.application.message;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;

/**
 * Pairs the {@link Messages} resolution port with the {@link MessageSink} delivery port so a use case tells a
 * viewer something in one call. Resolution happens in the viewer's locale and delivery hops to their region
 * thread, silently no-opping when they are offline.
 *
 * <p>This is the one send surface for every context, so a change to the pairing is a change in one place
 * rather than one copy per context. A context that needs more than a send (money formatting, a vault's named messages) wraps
 * this rather than repeating it.
 */
public final class Notifier {

    private final Messages messages;
    private final MessageSink sink;

    public Notifier(Messages messages, MessageSink sink) {
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

    /** Resolve and deliver a line chosen elsewhere, such as the reply to a player name that matched nobody. */
    public void send(PlayerRef viewer, MessageLine line) {
        send(viewer, line.key(), line.args());
    }

    /** Resolve and deliver {@code key} with no placeholders. */
    public void send(PlayerRef viewer, MessageKey key) {
        send(viewer, key, Map.of());
    }

    /**
     * Resolve {@code key} for {@code viewer} and return the source string without delivering it, for text that
     * leaves through something other than the chat sink: a kick or ban screen hands it to the disconnect
     * reason instead.
     */
    public String render(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        return messages.resolve(viewer, key, placeholders);
    }
}
