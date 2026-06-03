package com.uxplima.uxmessentials.holograms.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.event.HologramCreated;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /hologram create <name> <text…>}: create a server-wide hologram at the operator's current position
 * with one initial text line. A name a hologram already exists under is rejected with
 * {@link HologramError#NAME_TAKEN}; a brand-new name is stored, the live entity is spawned, and
 * {@code HologramCreated} is published. The operator-only permission to run this command is enforced at the
 * adapter gate; this use case owns the name-taken decision and the persistence.
 */
public final class CreateHologram {

    private final HologramRepository repository;
    private final HologramView view;
    private final HologramNotifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public CreateHologram(
            HologramRepository repository,
            HologramView view,
            HologramNotifier notifier,
            DomainEventPublisher events,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Create the hologram {@code name} at {@code at} with one {@code firstLine}, or reject a taken name. */
    public Result<Unit, HologramError> create(
            PlayerRef creator, HologramName name, Position at, HologramLine firstLine) {
        Objects.requireNonNull(creator, "creator");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(firstLine, "firstLine");
        if (repository.exists(name)) {
            notifier.send(creator, HologramError.NAME_TAKEN.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NAME_TAKEN);
        }
        Hologram hologram = Hologram.create(name, at, List.of(firstLine), clock.instant());
        repository.save(hologram);
        view.render(hologram);
        events.publish(new HologramCreated(name, creator, at));
        notifier.send(creator, HologramsMessageKey.HOLOGRAM_CREATED, Map.of("name", name.value()));
        return Result.ok();
    }
}
