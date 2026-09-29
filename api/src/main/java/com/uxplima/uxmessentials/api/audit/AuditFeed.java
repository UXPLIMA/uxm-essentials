package com.uxplima.uxmessentials.api.audit;

import java.util.function.Consumer;

import org.jspecify.annotations.NullMarked;

/**
 * Every line the host writes on its {@code com.uxplima.uxmessentials.audit} channel, handed to whoever subscribes.
 * The host registers one through Bukkit's {@code ServicesManager}. The optional Discord bridge looks it up and mirrors
 * what it hears (docs/09-deployment.md Path C and the audit logging section).
 *
 * <p>This contract lives in {@code :api} because a service two jars share must be one class to both of them. The
 * bridge declared its own source once, and no host could register a class it had never seen.
 *
 * <p>A listener runs on the thread that wrote the line, which may be a region thread or an async one. It must return
 * quickly and must not block. A listener that throws is logged by the host, and the line and the other listeners are
 * unaffected.
 */
@NullMarked
public interface AuditFeed {

    /**
     * Hear every audit line from now on.
     *
     * @param listener called once per line
     * @return the handle that stops delivery when closed
     */
    Subscription subscribe(Consumer<AuditEntry> listener);

    /** A live subscription. Closing it stops delivery, and closing it again does nothing. */
    @FunctionalInterface
    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
