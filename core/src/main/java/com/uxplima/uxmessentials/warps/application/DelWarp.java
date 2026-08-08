package com.uxplima.uxmessentials.warps.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmessentials.warps.domain.event.WarpDeleted;

/**
 * {@code /delwarp <name>}: remove a server-wide warp, freeing its name for reuse. A name no warp exists at
 * is rejected with {@link WarpError#NOT_FOUND}; a successful delete removes the row and publishes
 * {@code WarpDeleted} attributed to the staff member who ran the command. The operator-only permission is
 * enforced at the adapter gate.
 */
public final class DelWarp {

    private final WarpRepository repository;
    private final Notifier notifier;
    private final DomainEventPublisher events;

    public DelWarp(WarpRepository repository, Notifier notifier, DomainEventPublisher events) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Delete the warp {@code name}, or reject when no such warp exists. */
    public Result<Unit, WarpError> delete(PlayerRef actor, WarpName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        if (!repository.exists(name)) {
            notifier.send(actor, WarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(WarpError.NOT_FOUND);
        }
        repository.delete(name);
        events.publish(new WarpDeleted(name, actor));
        notifier.send(actor, WarpsMessageKey.WARP_DELETED, Map.of("warp", name.value()));
        return Result.ok();
    }
}
