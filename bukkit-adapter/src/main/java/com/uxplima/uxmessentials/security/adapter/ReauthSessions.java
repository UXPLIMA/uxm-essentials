package com.uxplima.uxmessentials.security.adapter;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The in-flight re-auth prompts: who is currently being asked to prove their second factor before a protected command
 * runs, and the exact command line to retry once they do. A player is added when they run a protected command they
 * have not recently verified for, and removed when they verify, abandon the keypad, or disconnect. Presence of the key
 * is what routes a keypad submission to the re-auth flow rather than the join-verification flow.
 *
 * <p>Transient in-memory state owned solely by the security adapter and distinct from the join-verification
 * {@link VerificationSessions}: a re-auth does not freeze the player (they chose to run the command), so the two never
 * overlap for one player. The map is a {@link ConcurrentHashMap} because the command listener, the keypad verify
 * worker and the quit handler all touch it; every mutation is a single atomic operation. Everything is dropped on
 * {@link #clearAll()} at module stop.
 */
@NullMarked
public final class ReauthSessions {

    /** UUID → the prompt pending for them, with the command line to retry; presence means a re-auth is pending. */
    private final ConcurrentHashMap<UUID, Prompt> pending = new ConcurrentHashMap<>();

    /** Hands out the tokens. Monotonic for the process lifetime, so no two prompts ever share one. */
    private final AtomicLong tokens = new AtomicLong();

    /** Mark {@code playerId} as awaiting a re-auth to run {@code commandLine}, and return the token of this prompt. */
    public long begin(UUID playerId, String commandLine) {
        long token = tokens.incrementAndGet();
        pending.put(
                Objects.requireNonNull(playerId, "playerId"),
                new Prompt(Objects.requireNonNull(commandLine, "commandLine"), token));
        return token;
    }

    /** The token of {@code playerId}'s pending prompt, empty when none is pending. */
    public OptionalLong token(UUID playerId) {
        Prompt prompt = pending.get(Objects.requireNonNull(playerId, "playerId"));
        return prompt == null ? OptionalLong.empty() : OptionalLong.of(prompt.token());
    }

    /** Whether {@code playerId} is still in the specific prompt that {@code token} identifies. */
    public boolean isPending(UUID playerId, long token) {
        Prompt prompt = pending.get(Objects.requireNonNull(playerId, "playerId"));
        return prompt != null && prompt.token() == token;
    }

    /** Whether {@code playerId} is currently being asked to re-authenticate for a protected command. */
    public boolean isPending(UUID playerId) {
        return pending.containsKey(Objects.requireNonNull(playerId, "playerId"));
    }

    /** The command line {@code playerId} is re-authenticating for, or {@code null} when none is pending. */
    public @Nullable String pendingCommand(UUID playerId) {
        Prompt prompt = pending.get(Objects.requireNonNull(playerId, "playerId"));
        return prompt == null ? null : prompt.commandLine();
    }

    /** Clear {@code playerId}'s pending re-auth, on a successful proof, an abandon, or a disconnect. */
    public void clear(UUID playerId) {
        pending.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    /** Drop every pending re-auth, called on module stop so no prompt survives a disable. */
    public void clearAll() {
        pending.clear();
    }

    private record Prompt(String commandLine, long token) {}
}
