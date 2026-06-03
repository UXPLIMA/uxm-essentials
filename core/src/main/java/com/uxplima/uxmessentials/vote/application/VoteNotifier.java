package com.uxplima.uxmessentials.vote.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Pairs the {@link Messages} resolution port with the {@link MessageSink} delivery port so a vote use
 * case sends one of the plugin's own {@link MessageKey} strings to a viewer in one call — the thank-you
 * broadcast, the vote-party announcement, the {@code /vote} and {@code /voteparty} lines, and the
 * test-reward confirmation. Resolution happens in the viewer's locale and delivery hops to the viewer's
 * region thread, silently no-opping when they are offline.
 *
 * <p>This notifier carries only {@code MessageKey} content — the plugin's own strings. The
 * operator-authored vote-links and reward commands do <em>not</em> flow through here; they are config
 * content rendered/dispatched as written, keeping the parity-checked keys and the unchecked operator
 * content cleanly apart.
 */
public final class VoteNotifier {

    private final Messages messages;
    private final MessageSink sink;

    public VoteNotifier(Messages messages, MessageSink sink) {
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
