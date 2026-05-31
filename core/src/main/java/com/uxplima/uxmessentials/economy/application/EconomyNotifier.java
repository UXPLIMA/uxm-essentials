package com.uxplima.uxmessentials.economy.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Pairs the {@link Messages} resolution port with the {@link MessageSink} delivery port so an economy use
 * case sends a {@link MessageKey} to a viewer in one call. Resolution happens in the viewer's locale and
 * delivery hops to the viewer's region thread, silently no-opping when they are offline. Mirrors the
 * homes/warps notifiers rather than sharing one, keeping each context's send surface its own.
 *
 * <p>It also owns the configured {@link AmountFormat}, so every economy use case renders a {@link Money}
 * placeholder through {@link #amount(Money)} and the operator's {@code economy.amount-format} choice applies
 * uniformly across {@code /balance}, {@code /pay}, {@code /baltop}, and {@code /eco} without each use case
 * carrying the toggle.
 */
public final class EconomyNotifier {

    private final Messages messages;
    private final MessageSink sink;
    private final AmountFormat amountFormat;

    public EconomyNotifier(Messages messages, MessageSink sink) {
        this(messages, sink, AmountFormat.FULL);
    }

    public EconomyNotifier(Messages messages, MessageSink sink, AmountFormat amountFormat) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.amountFormat = Objects.requireNonNull(amountFormat, "amountFormat");
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

    /** Render {@code money} with its symbol in the configured {@link AmountFormat} — the {@code {amount}} value. */
    public String amount(Money money) {
        return MoneyFormat.withSymbol(money, amountFormat);
    }
}
