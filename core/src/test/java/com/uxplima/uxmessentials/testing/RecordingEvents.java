package com.uxplima.uxmessentials.testing;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/** A publisher that keeps the events it was given. */
public final class RecordingEvents implements DomainEventPublisher {

    private final List<DomainEvent> published = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
        published.add(event);
    }

    public List<DomainEvent> published() {
        return List.copyOf(published);
    }
}
