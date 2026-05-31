package com.uxplima.uxmessentials.communication.application;

import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.communication.domain.MessagePolicy;
import com.uxplima.uxmessentials.communication.domain.PlaceholderBindings;

/**
 * Resolves the join message for a connecting player by applying the join channel's {@link MessagePolicy} and the
 * event's placeholders through the shared {@link ResolveConnectionMessage} engine. The policy is read fresh each
 * call from the supplied {@code policy} holder, so a {@code /uxmess reload communication} that swaps in a new
 * {@code AtomicReference<MessagePolicy>} takes effect on the next join with no re-wiring.
 *
 * <p>The standard join tokens — {@code {player}} (display name), {@code {count}} (online count after join),
 * {@code {world}} — are bound by the adapter from the live join event and passed in here; this use case never
 * touches a Bukkit type, and the returned {@link ResolvedMessage} carries operator content for the adapter to
 * render through MiniMessage.
 */
public final class ResolveJoinMessage {

    static final String CHANNEL = "join";

    private final ResolveConnectionMessage engine;
    private final Supplier<MessagePolicy> policy;

    public ResolveJoinMessage(ResolveConnectionMessage engine, Supplier<MessagePolicy> policy) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** The resolved join message for the event described by {@code bindings}. */
    public ResolvedMessage resolve(PlaceholderBindings bindings) {
        Objects.requireNonNull(bindings, "bindings");
        return engine.resolve(CHANNEL, policy.get(), bindings);
    }
}
