package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Renders a {@link MessageKey} in the viewer's locale and delivers it through the {@link MessageSink} — the
 * one path every moderation use case uses to tell the actor or the target what happened. Split from the
 * audit trail on purpose: this is the player-facing channel ({@code MessageKey} catalog), the
 * {@link com.uxplima.uxmessentials.moderation.application.port.ModerationAudit} is the operator-facing
 * {@code com.uxplima.uxmessentials.audit} channel; the two never share a string.
 */
public final class ModerationNotifier {

    private final Messages messages;
    private final MessageSink sink;

    public ModerationNotifier(Messages messages, MessageSink sink) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** Render {@code key} for {@code viewer} with no placeholders and deliver it. */
    public void send(PlayerRef viewer, MessageKey key) {
        send(viewer, key, Map.of());
    }

    /** Render {@code key} for {@code viewer} with {@code placeholders} and deliver it. */
    public void send(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        sink.deliver(viewer, messages.resolve(viewer, key, placeholders));
    }

    /**
     * Render {@code key} for {@code viewer} with {@code placeholders} and return the source string without
     * delivering it — used for a kick/ban screen, where the text is handed to the disconnect reason rather
     * than the chat sink.
     */
    public String render(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        return messages.resolve(viewer, key, placeholders);
    }
}
