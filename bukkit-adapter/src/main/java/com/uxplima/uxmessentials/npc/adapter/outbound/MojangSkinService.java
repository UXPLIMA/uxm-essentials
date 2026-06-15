package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uxplima.uxmessentials.npc.application.port.SkinService;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves any Minecraft account's signed skin from its username through Mojang's public API, the source for
 * {@code /npc skin <name> name:<username>}. The lookup is the well-known two-step dance:
 *
 * <ol>
 *   <li>{@code GET https://api.mojang.com/users/profiles/minecraft/<username>} → the canonical name and the
 *       undashed profile uuid (a {@code 404}/empty body means the name resolves to no account);</li>
 *   <li>{@code GET https://sessionserver.mojang.com/session/minecraft/profile/<uuid>?unsigned=false} → the
 *       profile properties, of which the {@code textures} property carries the base64 value and its signature.</li>
 * </ol>
 *
 * <p>The blocking HTTP runs on the {@link Scheduler}'s async pool, never a tick thread; the returned future is
 * completed from there. Every miss — an unknown name, a profile with no {@code textures} property, a non-200, a
 * timeout, malformed JSON — completes the future with {@link Optional#empty()} after logging the cause with the
 * username; the future never completes exceptionally. A small bounded Caffeine cache keyed by the lower-cased
 * username holds the result (present or absent) for an hour, so repeat {@code name:} lookups and command retries
 * do not hammer the rate-limited name→uuid endpoint.
 */
@NullMarked
public final class MojangSkinService implements SkinService {

    private static final String NAME_TO_UUID = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String PROFILE = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String SIGNED_QUERY = "?unsigned=false";

    private static final long MAX_CACHED_NAMES = 512L;
    private static final Duration CACHE_TTL = Duration.ofHours(1L);

    private final Scheduler scheduler;
    private final Logger log;
    private final HttpFetcher fetcher;
    private final Cache<String, Optional<NpcSkin>> cache;

    public MojangSkinService(Scheduler scheduler, Logger log, HttpFetcher fetcher) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHED_NAMES)
                .expireAfterWrite(CACHE_TTL)
                .build();
    }

    @Override
    public CompletableFuture<Optional<NpcSkin>> fetchByName(String username) {
        Objects.requireNonNull(username, "username");
        String key = username.strip().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Optional<NpcSkin> cached = cache.getIfPresent(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<Optional<NpcSkin>> result = new CompletableFuture<>();
        scheduler.async(() -> result.complete(load(key)));
        return result;
    }

    /** The blocking two-step resolve for an already-normalised {@code key}, caching and returning the outcome. */
    private Optional<NpcSkin> load(String key) {
        Optional<NpcSkin> skin = resolve(key);
        cache.put(key, skin);
        return skin;
    }

    private Optional<NpcSkin> resolve(String username) {
        Optional<URI> nameUri = uri(NAME_TO_UUID + username);
        if (nameUri.isEmpty()) {
            log.debug("NPC skin username {} is not a valid Mojang name lookup target", username);
            return Optional.empty();
        }
        Optional<String> profileBody = fetcher.get(nameUri.get());
        if (profileBody.isEmpty()) {
            log.debug("No Mojang profile resolved for NPC skin username {}", username);
            return Optional.empty();
        }
        Optional<String> id = MojangProfileJson.uuid(profileBody.get());
        if (id.isEmpty()) {
            log.warn("Mojang name lookup for NPC skin username {} returned no id", username);
            return Optional.empty();
        }
        Optional<URI> profileUri = uri(PROFILE + id.get() + SIGNED_QUERY);
        if (profileUri.isEmpty()) {
            log.warn("Mojang profile id {} for NPC skin username {} is not a valid session target", id.get(), username);
            return Optional.empty();
        }
        Optional<String> textureBody = fetcher.get(profileUri.get());
        if (textureBody.isEmpty()) {
            log.warn("Mojang session profile for NPC skin username {} ({}) returned no body", username, id.get());
            return Optional.empty();
        }
        Optional<NpcSkin> skin = MojangProfileJson.skin(textureBody.get());
        if (skin.isEmpty()) {
            log.warn("Mojang session profile for NPC skin username {} carried no textures property", username);
        }
        return skin;
    }

    /**
     * Build a request {@link URI}, or empty when {@code spec} is not a valid URI. A real Mojang username is
     * {@code [A-Za-z0-9_]{1,16}}, so a name carrying a space, a backslash, or a stray percent escape can never
     * form a legal path segment and is definitively not an account — treat it as a miss rather than letting
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
