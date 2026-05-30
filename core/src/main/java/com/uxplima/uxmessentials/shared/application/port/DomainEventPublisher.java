package com.uxplima.uxmessentials.shared.application.port;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * Outbound port for raising a {@link DomainEvent} onto the in-process bus.
 *
 * <p>Application services publish through this narrow contract after an aggregate has produced an
 * event; the adapter bridges each event to the corresponding Bukkit event so other plugins can
 * observe it. The port accepts the cross-cutting {@link DomainEvent} marker so any context's sealed
 * sub-interface flows through one method without the kernel knowing the concrete event set.
 */
public interface DomainEventPublisher {

    /** Publish a single domain event. Delivery to subscribers is the adapter's concern. */
    void publish(DomainEvent event);
}
