package com.uxplima.uxmessentials.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/** A {@link Notifier} whose catalog echoes the key, and which keeps every line it delivered. */
public final class RecordingNotifier {

    /** One delivered line: who read it, the catalog key and its placeholders. */
    public record Sent(PlayerRef viewer, String key, Map<String, String> placeholders) {}

    private final List<Sent> sent = new ArrayList<>();
    private final Notifier notifier;

    public RecordingNotifier() {
        List<Sent> lines = sent;
        this.notifier = new Notifier(
                (PlayerRef viewer, MessageKey key, Map<String, String> placeholders) -> {
                    lines.add(new Sent(viewer, key.key(), Map.copyOf(placeholders)));
                    return key.key();
                },
                (viewer, text) -> {});
    }

    public Notifier notifier() {
        return notifier;
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    public List<String> keys() {
        return sent.stream().map(Sent::key).toList();
    }
}
