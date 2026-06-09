package com.uxplima.uxmessentials.economy.fakes;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A {@link MessageSink} that records every delivery so a test can assert which messages reached which
 * viewer, in order. Paired with {@link KeyMessages}, the recorded text is the catalog key, so an assertion
 * reads as a claim about which {@code MessageKey} a use case sent.
 */
public final class CapturingSink implements MessageSink {

    /** One delivered message: the viewer and the rendered (key-based) text. */
    public record Sent(PlayerRef viewer, String text) {}

    private final List<Sent> sent = new ArrayList<>();

    @Override
    public void deliver(PlayerRef viewer, String renderedText) {
        sent.add(new Sent(viewer, renderedText));
    }

    /** Every delivery so far, in order. */
    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    /** True when some delivery's text starts with {@code keyPrefix} (the catalog key under {@link KeyMessages}). */
    public boolean delivered(String keyPrefix) {
        return sent.stream().anyMatch(s -> s.text().startsWith(keyPrefix));
    }

    /** How many deliveries so far whose text starts with {@code keyPrefix}. */
    public long count(String keyPrefix) {
        return sent.stream().filter(s -> s.text().startsWith(keyPrefix)).count();
    }
}
