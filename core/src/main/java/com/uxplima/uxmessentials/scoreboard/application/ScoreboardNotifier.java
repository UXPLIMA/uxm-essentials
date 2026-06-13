package com.uxplima.uxmessentials.scoreboard.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Pairs the {@link Messages} resolution port with the {@link MessageSink} delivery port so a scoreboard use case
 * sends one of the plugin's own {@link MessageKey} strings to a viewer in one call — the {@code /scoreboard}
 * shown/hidden confirmations. Resolution happens in the viewer's locale and delivery hops to the viewer's region
 * thread, silently no-opping when they are offline.
 *
 * <p>This notifier carries only {@code MessageKey} content — the plugin's own strings. The operator-authored sidebar
 * title and sidebar lines do <em>not</em> flow through here: the renderer parses them straight from their raw
 * MiniMessage, keeping the parity-checked keys and the unchecked operator content cleanly apart.
 */
public final class ScoreboardNotifier {

    private final Messages messages;
    private final MessageSink sink;

    public ScoreboardNotifier(Messages messages, MessageSink sink) {
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
