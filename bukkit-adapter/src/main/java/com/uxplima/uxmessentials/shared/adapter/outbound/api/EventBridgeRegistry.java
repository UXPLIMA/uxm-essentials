package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The table that says which published Bukkit event a domain fact becomes, and where to deliver it.
 *
 * <p>One entry per bridged domain event, added by each context's own bridge class. Three things per entry, and each
 * earns its place:
 *
 * <ul>
 *   <li>the <b>handler list</b> of the Bukkit event, so the bridge can ask whether anybody is listening before it
 *       builds anything. With no listener a publish costs one map lookup and an array-length read;
 *   <li>the <b>mapper</b>, which is only ever called once that check has passed;
 *   <li>the <b>region</b>, because a notification is delivered on a tick thread and Folia has more than one. An
 *       event about a player goes to that player's region, one about a place to that place's, and only genuinely
 *       server-wide facts go global.
 * </ul>
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>build-then-publish</b>. Every entry is added during wiring, on one thread, before the registry is
 * handed to the bridge; afterwards it is read-only, so the plain map needs no synchronisation. {@link #register}
 * refuses a second entry for the same domain event, which turns a duplicated bridge line into a loud wiring failure
 * rather than a silently ignored one.
 */
@NullMarked
public final class EventBridgeRegistry {

    private final Map<Class<? extends DomainEvent>, Entry<?>> entries = new LinkedHashMap<>();

    /**
     * Bridge {@code type} onto the Bukkit event {@code mapper} builds, delivered on the region {@code region}
     * picks for that fact.
     */
    public <E extends DomainEvent> EventBridgeRegistry register(
            Class<E> type, HandlerList handlers, Function<E, ? extends UxmEvent> mapper, Function<E, Region> region) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handlers, "handlers");
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(region, "region");
        Entry<?> previous = entries.putIfAbsent(type, new Entry<>(handlers, mapper, region));
        if (previous != null) {
            throw new IllegalStateException(type.getName() + " is already bridged; each fact maps to one event");
        }
        return this;
    }

    /** The entry for this fact, or {@code null} when the fact is internal and deliberately not published. */
    @SuppressWarnings("unchecked") // the map is only ever written through register, which pairs key and value types
    <E extends DomainEvent> @Nullable Entry<E> lookup(E event) {
        return (Entry<E>) entries.get(event.getClass());
    }

    /** Every bridged domain-event type, for the coverage guard that pins what is published. */
    public Set<Class<? extends DomainEvent>> bridged() {
        return Set.copyOf(entries.keySet());
    }

    /** One row of the table. */
    record Entry<E extends DomainEvent>(
            HandlerList handlers, Function<E, ? extends UxmEvent> mapper, Function<E, Region> region) {

        boolean hasListeners() {
            return handlers.getRegisteredListeners().length > 0;
        }
    }
}
