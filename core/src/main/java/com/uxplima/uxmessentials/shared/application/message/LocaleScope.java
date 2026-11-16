package com.uxplima.uxmessentials.shared.application.message;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * The request-scoped carrier for the requesting player's resolved locale.
 *
 * <p>A command runs on its sender's thread, where the sender's client language can be read fresh. The command
 * boundary reads it once and binds it here, together with who the sender is, so every line the handler writes to the
 * sender renders in that language without a {@code Locale} threaded through every signature.
 *
 * <p>The binding answers for the sender only. A line the same handler writes to somebody else (the player a warning
 * names, the target of a ban screen) is rendered in that reader's own language, which the resolver knows for every
 * online player; a staff member's language must never leak into the words their target reads.
 *
 * <p>A plain {@link ThreadLocal} backs the binding (not {@code ScopedValue}, which is a Java-21 preview API and would
 * version-lock the compiled classes to a single JDK). {@link #runWith} sets the value for the duration of the call and
 * restores the previous binding in a {@code finally} block, so nothing leaks across a pooled worker thread. When a
 * handler hops to a different executor the binding does not follow, and the resolver then reads the recipient's own
 * client locale. The bound value is an immutable {@link Locale}; reading the client locale off the Bukkit API happens
 * once on the region thread at the boundary.
 */
public final class LocaleScope {

    private static final ThreadLocal<Binding> CURRENT = new ThreadLocal<>();

    private LocaleScope() {}

    /**
     * Run {@code body} with {@code locale} bound as {@code requester}'s language for the duration of the call. The
     * previous binding (if any) is restored when {@code body} returns, so nested binds and pooled threads never leak a
     * stale locale.
     */
    public static void runWith(UUID requester, Locale locale, Runnable body) {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(body, "body");
        Binding previous = CURRENT.get();
        CURRENT.set(new Binding(requester, locale));
        try {
            body.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** The locale bound for {@code viewer} on this thread, if they are the requester, otherwise {@code fallback}. */
    public static Locale forViewer(UUID viewer, Locale fallback) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(fallback, "fallback");
        Binding current = CURRENT.get();
        return current != null && current.requester().equals(viewer) ? current.locale() : fallback;
    }

    private record Binding(UUID requester, Locale locale) {}
}
