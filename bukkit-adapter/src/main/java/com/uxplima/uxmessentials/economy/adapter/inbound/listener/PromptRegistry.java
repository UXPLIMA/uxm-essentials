package com.uxplima.uxmessentials.economy.adapter.inbound.listener;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.jspecify.annotations.NullMarked;

/**
 * Tracks the pending chat prompts for an economy menu flow, shared by the bank/loan/exchange listeners. Each
 * entry carries a deadline so a prompt the player never answers expires instead of lingering until they next
 * chat; {@link #register} overwrites any prompt already pending for that player (the overlap guard — opening a
 * new prompt cancels the stale one); {@link #clear} drops every pending prompt when the module stops, so a
 * disabled module holds no runtime state and a leftover callback can never fire after teardown.
 */
@NullMarked
final class PromptRegistry {

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Duration expiry;

    PromptRegistry(Duration expiry) {
        this.expiry = Objects.requireNonNull(expiry, "expiry");
    }

    /** Register {@code callback} for {@code player}, replacing any prompt already pending for them. */
    void register(UUID player, Consumer<String> callback) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(callback, "callback");
        pending.put(player, new Pending(callback, System.currentTimeMillis() + expiry.toMillis()));
    }

    /** Remove and return the live (non-expired) callback for {@code player}, if any. */
    Optional<Consumer<String>> take(UUID player) {
        Pending entry = pending.remove(player);
        if (entry == null || System.currentTimeMillis() > entry.deadline()) {
            return Optional.empty();
        }
        return Optional.of(entry.callback());
    }

    /** Drop every pending prompt; called on module stop. */
    void clear() {
        pending.clear();
    }

    int size() {
        return pending.size();
    }

    private record Pending(Consumer<String> callback, long deadline) {}
}
