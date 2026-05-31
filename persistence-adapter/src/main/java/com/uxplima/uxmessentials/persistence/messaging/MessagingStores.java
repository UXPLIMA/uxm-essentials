package com.uxplima.uxmessentials.persistence.messaging;

import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the messaging context's persistence adapters, so the consuming bukkit-adapter wires the
 * {@link MailRepository} and {@link IgnoreStore} from the {@link Persistence} handle it already holds without
 * ever naming a jOOQ type (jOOQ is an {@code implementation} dependency of this module, kept off the
 * consumer's compile classpath).
 *
 * <p>The mailbox repository is the plain jOOQ adapter — a box mutates on every read (mark-all-read) and on
 * the expiry sweep, so it is left uncached for correctness rather than fighting invalidation. The ignore
 * store is the jOOQ adapter behind a Caffeine read-cache (the ignore list is read once per delivered
 * message, so caching the small per-owner list pays off; write-through at the delegate, invalidate in the
 * cache).
 */
@NullMarked
public final class MessagingStores {

    private MessagingStores() {}

    /** A jOOQ {@link MailRepository} over the shared persistence DSL. */
    public static MailRepository mail(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqMailRepository(persistence.dsl());
    }

    /** A cached jOOQ {@link IgnoreStore} over the shared persistence DSL. */
    public static IgnoreStore ignores(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new CachedIgnoreStore(new JooqIgnoreStore(persistence.dsl()));
    }
}
