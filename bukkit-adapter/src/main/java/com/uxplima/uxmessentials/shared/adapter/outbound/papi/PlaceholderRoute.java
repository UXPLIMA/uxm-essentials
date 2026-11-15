package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderSpec;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Which placeholders one registered expansion answers, and under which PlaceholderAPI identifier.
 *
 * <p>PlaceholderAPI finds an expansion by the text up to the first underscore, so {@code %uxm_essentials_balance%}
 * reaches the identifier {@code uxm} with {@code essentials_balance} as its parameters, and an identifier that itself
 * holds an underscore is never asked at all. The current route therefore registers the part of
 * {@link PlaceholderSpec#PREFIX} before its first underscore and answers only the parameters that start with the rest,
 * so another plugin's {@code uxm_} placeholders are left alone.
 *
 * <p>The legacy route keeps {@code %uxmessentials_<key>%} answering, because those placeholders sit in other plugins'
 * files too (a tab list, a scoreboard, a hologram plugin) where nothing of ours can rewrite them. It says so in the
 * log the first time one is read, naming the key, so the operator knows what to edit before the old form goes.
 */
@NullMarked
final class PlaceholderRoute {

    /** The identifier the older placeholders are written with. */
    static final String LEGACY_IDENTIFIER = "uxmessentials";

    private final String identifier;
    private final String namespace;
    private final Consumer<String> onFirstUse;
    private final AtomicBoolean used = new AtomicBoolean();

    private PlaceholderRoute(String identifier, String namespace, Consumer<String> onFirstUse) {
        this.identifier = identifier;
        this.namespace = namespace;
        this.onFirstUse = onFirstUse;
    }

    /** The route for {@code %uxm_essentials_<key>%} and {@code %rel_uxm_essentials_<key>%}. */
    static PlaceholderRoute current() {
        String prefix = PlaceholderSpec.PREFIX;
        int split = prefix.indexOf('_');
        return new PlaceholderRoute(prefix.substring(0, split), prefix.substring(split + 1), key -> {});
    }

    /** The route for {@code %uxmessentials_<key>%}, which warns once through {@code log} when it is first read. */
    static PlaceholderRoute legacy(Logger log) {
        Objects.requireNonNull(log, "log");
        return new PlaceholderRoute(
                LEGACY_IDENTIFIER,
                "",
                key -> log.warn(
                        "event=papi_placeholder_deprecated read=%{}_{}% use=%{}{}% note=the old form stops answering in"
                                + " a later release, so edit every file that still holds it",
                        LEGACY_IDENTIFIER, key, PlaceholderSpec.PREFIX, key));
    }

    /** The PlaceholderAPI identifier the expansion registers under. */
    String identifier() {
        return identifier;
    }

    /**
     * The key PlaceholderAPI's {@code params} ask this route for, or empty when the parameters belong to someone else
     * sharing the identifier.
     */
    Optional<String> key(String params) {
        Objects.requireNonNull(params, "params");
        if (!params.toLowerCase(Locale.ROOT).startsWith(namespace) || params.length() == namespace.length()) {
            return Optional.empty();
        }
        String key = params.substring(namespace.length());
        if (used.compareAndSet(false, true)) {
            onFirstUse.accept(key);
        }
        return Optional.of(key);
    }
}
