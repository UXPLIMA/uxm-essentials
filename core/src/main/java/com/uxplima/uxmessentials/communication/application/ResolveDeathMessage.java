package com.uxplima.uxmessentials.communication.application;

import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.communication.domain.MessagePolicy;
import com.uxplima.uxmessentials.communication.domain.PlaceholderBindings;

/**
 * Resolves the death message for a player by applying the death channel's {@link MessagePolicy} and the event's
 * placeholders through the shared {@link ResolveConnectionMessage} engine. The policy is read fresh each call from
 * the supplied holder, so a reload that swaps the death policy takes effect on the next death.
 *
 * <p>The standard death tokens — {@code {player}} (the victim's display name), {@code {killer}} (the killer's
 * name when one applies), {@code {cause}} — are bound by the adapter from the live death event, which already
 * holds the vanilla death cause; this use case never touches a Bukkit type. The returned {@link ResolvedMessage}
 * carries operator content for the adapter to render through MiniMessage.
 */
public final class ResolveDeathMessage {

    static final String CHANNEL = "death";

    private final ResolveConnectionMessage engine;
    private final Supplier<MessagePolicy> policy;

    public ResolveDeathMessage(ResolveConnectionMessage engine, Supplier<MessagePolicy> policy) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** The resolved death message for the event described by {@code bindings}. */
    public ResolvedMessage resolve(PlaceholderBindings bindings) {
        Objects.requireNonNull(bindings, "bindings");
        return engine.resolve(CHANNEL, policy.get(), bindings);
    }
}
