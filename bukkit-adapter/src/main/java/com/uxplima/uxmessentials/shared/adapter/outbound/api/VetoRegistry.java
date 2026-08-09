package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmCancellableEvent;
import com.uxplima.uxmessentials.shared.domain.DomainProposal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The table that says which published pre-event a domain proposal becomes.
 *
 * <p>The sibling of {@link EventBridgeRegistry}, and deliberately not merged with it: a fact is a notification with a
 * region to deliver it on, while a proposal is a question answered on the asking thread, so the two rows carry
 * different things and are consulted at different moments. Keeping them apart is also what makes "which operations
 * can be vetoed" a list somebody can read.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>build-then-publish</b>, exactly as {@link EventBridgeRegistry}. Every entry is added during wiring on
 * one thread, and the table is read-only from the moment the gate holds it.
 */
@NullMarked
public final class VetoRegistry {

    private final Map<Class<? extends DomainProposal>, Entry<?>> entries = new LinkedHashMap<>();

    /** Ask about {@code type} through the Bukkit event {@code mapper} builds. */
    public <P extends DomainProposal> VetoRegistry register(
            Class<P> type, HandlerList handlers, Function<P, ? extends UxmCancellableEvent> mapper) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handlers, "handlers");
        Objects.requireNonNull(mapper, "mapper");
        Entry<?> previous = entries.putIfAbsent(type, new Entry<>(handlers, mapper));
        if (previous != null) {
            throw new IllegalStateException(type.getName() + " is already vetoable; each proposal asks one event");
        }
        return this;
    }

    /** The entry for this proposal, or {@code null} when nothing published can refuse it. */
    @SuppressWarnings("unchecked") // the map is only ever written through register, which pairs key and value types
    <P extends DomainProposal> @Nullable Entry<P> lookup(P proposal) {
        return (Entry<P>) entries.get(proposal.getClass());
    }

    /** Every vetoable proposal type, for the guard that pins what can be refused. */
    public Set<Class<? extends DomainProposal>> vetoable() {
        return Set.copyOf(entries.keySet());
    }

    /** One row of the table. */
    record Entry<P extends DomainProposal>(HandlerList handlers, Function<P, ? extends UxmCancellableEvent> mapper) {

        boolean hasListeners() {
            return handlers.getRegisteredListeners().length > 0;
        }
    }
}
