package com.uxplima.uxmessentials.shared.adapter.outbound.skin;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves any Minecraft account's signed skin from its username, the one skin fetch the whole plugin shares:
 * {@code /npc skin <name>}, a tablist entry dressed in {@code player:<name>}, and anything else that needs a
 * texture for a name. The lookup is the well-known two-step dance:
 *
 * <ol>
 *   <li>{@code GET https://api.mojang.com/users/profiles/minecraft/<username>} yields the canonical name and the
 *       undashed profile uuid (a {@code 404} or empty body means the name resolves to no account);</li>
 *   <li>{@code GET https://sessionserver.mojang.com/session/minecraft/profile/<uuid>?unsigned=false} yields the
 *       profile properties, of which {@code textures} carries the base64 value and its signature.</li>
 * </ol>
 *
 * <p>Asking Mojang directly is what makes this work on an offline-mode (cracked) server. Bukkit's own
 * {@code PlayerProfile.complete()} consults the session service only when the server is in online mode, so a
 * profile completed there comes back with no textures at all and every skin by name is blank. This class does
 * not care what mode the server is in.
 *
 * <p>Every miss (an unknown name, a profile with no {@code textures} property, a non-200, a timeout, malformed
 * JSON) is an empty {@link Optional} after logging the cause with the username; nothing here throws and no
 * future completes exceptionally. A bounded Caffeine cache keyed by the lower-cased username holds the result,
 * present or absent, for an hour, so repeat lookups and command retries do not hammer the rate-limited
 * name-to-uuid endpoint. The cache is shared across contexts because the instance is: the kernel builds one.
 */
@NullMarked
public final class MojangSkins implements SkinTextures {

    private static final String NAME_TO_UUID = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String PROFILE = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String SIGNED_QUERY = "?unsigned=false";

    private static final long MAX_CACHED_NAMES = 512L;
    private static final Duration CACHE_TTL = Duration.ofHours(1L);

    private final Scheduler scheduler;
    private final Logger log;
    private final HttpFetcher fetcher;
    private final boolean enabled;
    private final Cache<String, Optional<SkinTexture>> cache;

    /** A lookup that asks Mojang. */
    public MojangSkins(Scheduler scheduler, Logger log, HttpFetcher fetcher) {
        this(scheduler, log, fetcher, true);
    }

    /**
     * A lookup that asks Mojang when {@code enabled}, and resolves nothing at all when it is not: an operator
     * running a server with no outbound network, or one who simply does not want the calls, sets
     * {@code skins.mojang-lookup = false} and every by-name resolve becomes an immediate miss.
     */
    public MojangSkins(Scheduler scheduler, Logger log, HttpFetcher fetcher, boolean enabled) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.enabled = enabled;
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHED_NAMES)
                .expireAfterWrite(CACHE_TTL)
                .build();
    }

    /**
     * Resolve {@code username}'s signed skin off-thread, completing with the texture or with
     * {@link Optional#empty()} for any miss. Never completes exceptionally; never blocks the calling thread, so
     * this is the entry point for a caller on a tick thread.
     */
    @Override
    public CompletableFuture<Optional<SkinTexture>> byName(String username) {
        Objects.requireNonNull(username, "username");
        String key = key(username);
        Optional<SkinTexture> known = known(key);
        if (known != null) {
            return CompletableFuture.completedFuture(known);
        }
        CompletableFuture<Optional<SkinTexture>> result = new CompletableFuture<>();
        // Guarantee the future always completes: a throw from the resolve seam (the injected fetcher, gson, the
        // cache) must not escape the async stage and orphan the future, or the operator hangs on the "fetching"
        // line forever. Any unexpected throw completes the future empty after logging.
        scheduler.async(() -> {
            try {
                result.complete(load(key));
            } catch (RuntimeException unexpected) {
                log.error("Unexpected failure resolving Mojang skin for username " + key, unexpected);
                result.complete(Optional.empty());
            }
        });
        return result;
    }

    /**
     * The blocking form of {@link #byName(String)}, for a caller that is already off a tick thread and wants the
     * answer inline. Same cache, same fail-soft contract.
     */
    @Override
    public Optional<SkinTexture> fetchNow(String username) {
        Objects.requireNonNull(username, "username");
        String key = key(username);
        Optional<SkinTexture> known = known(key);
        if (known != null) {
            return known;
        }
        try {
            return load(key);
        } catch (RuntimeException unexpected) {
            log.error("Unexpected failure resolving Mojang skin for username " + key, unexpected);
            return Optional.empty();
        }
    }

    private static String key(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }

    /** The answer already settled for {@code key}: a cache hit, an empty name, or the lookup being turned off. */
    private Optional<SkinTexture> known(String key) {
        if (key.isEmpty() || !enabled) {
            return Optional.empty();
        }
        return cache.getIfPresent(key);
    }

    /** The blocking two-step resolve for an already-normalised {@code key}, caching and returning the outcome. */
    private Optional<SkinTexture> load(String key) {
        Optional<SkinTexture> skin = resolve(key);
        cache.put(key, skin);
        return skin;
    }

    private Optional<SkinTexture> resolve(String username) {
        Optional<URI> nameUri = uri(NAME_TO_UUID + username);
        if (nameUri.isEmpty()) {
            log.debug("Skin username {} is not a valid Mojang name lookup target", username);
            return Optional.empty();
        }
        Optional<String> profileBody = fetcher.get(nameUri.get());
        if (profileBody.isEmpty()) {
            log.debug("No Mojang profile resolved for skin username {}", username);
            return Optional.empty();
        }
        Optional<String> id = MojangProfileJson.uuid(profileBody.get());
        if (id.isEmpty()) {
            log.warn("Mojang name lookup for skin username {} returned no id", username);
            return Optional.empty();
        }
        Optional<URI> profileUri = uri(PROFILE + id.get() + SIGNED_QUERY);
        if (profileUri.isEmpty()) {
            log.warn("Mojang profile id {} for skin username {} is not a valid session target", id.get(), username);
            return Optional.empty();
        }
        Optional<String> textureBody = fetcher.get(profileUri.get());
        if (textureBody.isEmpty()) {
            log.warn("Mojang session profile for skin username {} ({}) returned no body", username, id.get());
            return Optional.empty();
        }
        Optional<SkinTexture> skin = MojangProfileJson.skin(textureBody.get());
        if (skin.isEmpty()) {
            log.warn("Mojang session profile for skin username {} carried no textures property", username);
        }
        return skin;
    }

    /**
     * Build a request {@link URI}, or empty when {@code spec} is not a valid URI. A real Mojang username is
     * {@code [A-Za-z0-9_]{1,16}}, so a name carrying a space, a backslash, or a stray percent escape can never
     * form a legal path segment and is definitively not an account: treat it as a miss rather than letting
     * {@link URI#create(String)} throw out of the async stage and leave the command's future hanging.
     */
    private static Optional<URI> uri(String spec) {
        try {
            return Optional.of(URI.create(spec));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
