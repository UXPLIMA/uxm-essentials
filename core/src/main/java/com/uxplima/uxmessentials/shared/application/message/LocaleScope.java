package com.uxplima.uxmessentials.shared.application.message;

import java.util.Locale;
import java.util.Objects;

/**
 * The request-scoped carrier for the requesting player's resolved locale (docs/13-i18n §5,
 * docs/02-concurrency §4.3).
 *
 * <p>Most messages are sent synchronously on the command thread, where the viewer's locale is trivially
 * available. The hard case is the deferred message — a {@code /baltop} page rendered after an off-tick
 * DB read, an {@code /rtp} confirmation after an off-thread safe-location search — where the thread that
 * finally calls {@code messages.resolve(...)} is a worker that knows nothing about the requester. The
 * rule is: bind the resolved locale once at the command boundary, and let the resolver read it on
 * whatever thread it lands on rather than threading a {@code Locale} through every signature.
 *
 * <p>{@link java.lang.ScopedValue} (JEP 446) is used rather than a {@link ThreadLocal}: it auto-unbinds
 * when the bound block returns — there is nothing to leak across millions of virtual threads — and it is
 * immutable for the dynamic scope, so a worker cannot accidentally rebind another request's locale. The
 * bound value is a plain immutable {@link Locale}; capturing the client locale off the Bukkit API
 * happens once on the region thread at the boundary, and only the result travels here.
 */
public final class LocaleScope {

    /**
     * Bound at the command/adapter boundary; read by the locale resolver on any thread within the
     * dynamic scope. When unbound (a path that never ran through the boundary), the resolver falls back
     * to its own chain rather than this value.
     */
    public static final ScopedValue<Locale> CURRENT = ScopedValue.newInstance();

    private LocaleScope() {}

    /**
     * Run {@code body} with {@code locale} bound as {@link #CURRENT} for the duration of the call and
     * every thread that inherits the scope. The binding unbinds automatically when {@code body} returns.
     */
    public static void runWith(Locale locale, Runnable body) {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(body, "body");
        ScopedValue.where(CURRENT, locale).run(body);
    }

    /** The bound locale if a boundary set one, otherwise {@code fallback}. Never returns {@code null}. */
    public static Locale orElse(Locale fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return CURRENT.isBound() ? CURRENT.get() : fallback;
    }
}
