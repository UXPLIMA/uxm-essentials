package com.uxplima.uxmessentials.economy.fakes;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/** A {@link DomainEventPublisher} that records every published event so a test can assert what was raised. */
public final class CapturingEvents implements DomainEventPublisher {

    private final List<DomainEvent> published = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
        published.add(event);
    }

    /** Every event published so far, in order. */
    public List<DomainEvent> published() {
        return List.copyOf(published);
    }
}
