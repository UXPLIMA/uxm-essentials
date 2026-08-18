package com.uxplima.uxmessentials.shared.application.port;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import org.jspecify.annotations.NullMarked;

/**
 * Outbound port for turning a player name into the skin that account wears: an NPC dressed with
 * {@code /npc skin <name>}, a tablist entry dressed with {@code player:<name>}, anything else that needs a
 * texture for a name.
 *
 * <p>Deliberately a kernel port rather than a per-context one. A skin lookup is rate-limited and worth caching
 * once for the whole server, and the resolution must not depend on the server's online-mode setting: the
 * platform's own profile completion consults the session service only on an online-mode server, so a context
 * that reached for it directly would silently resolve nothing on a cracked server.
 *
 * <p>Fail-soft throughout: every miss (an unknown name, an account with no texture, a rate-limit, an outage) is
 * an empty {@link Optional}. Nothing here throws, and the future never completes exceptionally.
 */
@NullMarked
public interface SkinTextures {

    /**
     * Resolve {@code username}'s skin off-thread. Safe to call from a tick thread: the answer arrives on
     * whatever thread the adapter ran the lookup on.
     */
    CompletableFuture<Optional<SkinTexture>> byName(String username);

    /**
     * Resolve {@code username}'s skin inline, blocking on the lookup when it is not already cached. Only for a
     * caller that is already off a tick thread.
     */
    Optional<SkinTexture> fetchNow(String username);

    /**
     * Forget whatever is cached for {@code username}, so the next lookup goes back to the source.
     *
     * <p>The cache is what keeps a busy server from hammering Mojang, but it also means a player who changed
     * their skin there keeps wearing the old one here until it expires. This is the door out of that: staff drop
     * one name and the next lookup is fresh. The default is a no-op, for a lookup that caches nothing.
     */
    default void purge(String username) {
        // Nothing cached, nothing to forget.
    }

    /** A lookup that resolves nothing: the default in tests and wherever no real source has been wired. */
    static SkinTextures none() {
        return NoSkinTextures.INSTANCE;
    }
}
