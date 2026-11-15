package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderSpec;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Which placeholders one registered expansion answers, and under which PlaceholderAPI identifier.
 *
 * <p>PlaceholderAPI finds an expansion by the text up to the first underscore, so {@code %uxm_essentials_balance%}
 * reaches the identifier {@code uxm} with {@code essentials_balance} as its parameters, and an identifier that itself
 * holds an underscore is never asked at all. The current route therefore registers the part of
 * {@link PlaceholderSpec#PREFIX} before its first underscore and answers only the parameters that start with the rest;
 * anything else under {@code uxm} gets no answer, which PlaceholderAPI leaves in the text as written.
 *
 * <p>The legacy route keeps {@code %uxmessentials_<key>%} answering, because those placeholders sit in other plugins'
 * files too (a tab list, a scoreboard, a hologram plugin) where nothing of ours can rewrite them. It names each key in
 * the log the first time that key is read, so the operator gets the list of what to edit. The list is capped: a file
 * that builds keys from player names would otherwise fill the log, and past the cap one line says there are more.
 */
@NullMarked
final class PlaceholderRoute {

    /** The identifier the older placeholders are written with. */
    static final String LEGACY_IDENTIFIER = "uxmessentials";

    /** How many distinct old keys are named in the log before one line says there are more. */
    static final int WARNED_KEYS = 256;

    private final String identifier;
    private final String namespace;
    private final @Nullable Logger log;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean overflowed = new AtomicBoolean();

    private PlaceholderRoute(String identifier, String namespace, @Nullable Logger log) {
        this.identifier = identifier;
        this.namespace = namespace;
        this.log = log;
    }

    /** The route for {@code %uxm_essentials_<key>%} and {@code %rel_uxm_essentials_<key>%}. */
    static PlaceholderRoute current() {
        String prefix = PlaceholderSpec.PREFIX;
        int split = prefix.indexOf('_');
        return new PlaceholderRoute(prefix.substring(0, split), prefix.substring(split + 1), null);
    }

    /** The route for {@code %uxmessentials_<key>%}, which names each key it is asked for through {@code log}. */
    static PlaceholderRoute legacy(Logger log) {
        return new PlaceholderRoute(LEGACY_IDENTIFIER, "", Objects.requireNonNull(log, "log"));
    }

    /** The PlaceholderAPI identifier the expansion registers under. */
    String identifier() {
        return identifier;
    }

    /**
     * The key PlaceholderAPI's {@code params} ask this route for, or empty when the parameters are not one of ours.
     * Asking the legacy route counts as reading the old form even when no player is attached to the request.
     */
    Optional<String> key(String params) {
        Objects.requireNonNull(params, "params");
        if (!params.toLowerCase(Locale.ROOT).startsWith(namespace) || params.length() == namespace.length()) {
            return Optional.empty();
        }
        String key = params.substring(namespace.length());
        if (log != null) {
            warnOnce(log, key);
        }
        return Optional.of(key);
    }

    private void warnOnce(Logger log, String key) {
        String folded = key.toLowerCase(Locale.ROOT);
        if (warned.contains(folded)) {
            return;
        }
        if (warned.size() >= WARNED_KEYS) {
            if (overflowed.compareAndSet(false, true)) {
                log.warn(
                        "event=papi_placeholder_deprecated_more named={} note=more %{}_...% placeholders are in use;"
                                + " search every plugin folder for {}_",
                        WARNED_KEYS, LEGACY_IDENTIFIER, LEGACY_IDENTIFIER);
            }
            return;
        }
        if (warned.add(folded)) {
            log.warn(
                    "event=papi_placeholder_deprecated read=%{}_{}% use=%{}{}% note=the old form stops answering in"
                            + " a later release, so edit every file that still holds it",
                    LEGACY_IDENTIFIER, key, PlaceholderSpec.PREFIX, key);
        }
    }
}
