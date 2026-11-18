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

/**
 * Where our placeholders are answered. PlaceholderAPI finds an expansion by the text up to the first underscore, so
 * {@code %uxm_essentials_balance%} reaches the identifier {@code uxm} with {@code essentials_balance} as its
 * parameters. Every UXM plugin shares that one {@code uxm} expansion, which uxmLib registers, and each joins it with a
 * prefix of its own: ours is {@link #sharedPrefix()}, the part of {@link PlaceholderSpec#PREFIX} between its
 * underscores.
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

    private final Logger log;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean overflowed = new AtomicBoolean();

    private PlaceholderRoute(Logger log) {
        this.log = log;
    }

    /** The prefix we join the shared {@code uxm} expansion with, for {@code %uxm_essentials_<key>%}. */
    static String sharedPrefix() {
        String prefix = PlaceholderSpec.PREFIX;
        return prefix.substring(prefix.indexOf('_') + 1, prefix.length() - 1);
    }

    /** The route for {@code %uxmessentials_<key>%}, which names each key it is asked for through {@code log}. */
    static PlaceholderRoute legacy(Logger log) {
        return new PlaceholderRoute(Objects.requireNonNull(log, "log"));
    }

    /** The PlaceholderAPI identifier the expansion registers under. */
    String identifier() {
        return LEGACY_IDENTIFIER;
    }

    /**
     * The key PlaceholderAPI's {@code params} ask this route for, or empty when there is none. Asking counts as reading
     * the old form even when no player is attached to the request.
     */
    Optional<String> key(String params) {
        Objects.requireNonNull(params, "params");
        if (params.isEmpty()) {
            return Optional.empty();
        }
        warnOnce(params);
        return Optional.of(params);
    }

    private void warnOnce(String key) {
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
