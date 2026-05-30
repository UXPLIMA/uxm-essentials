package com.uxplima.uxmessentials.homes.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /delhome <name>}: remove one of the owner's homes, freeing a slot under their limit. A missing
 * name is rejected with {@link HomeError#NOT_FOUND}; a successful delete removes the row and publishes
 * {@code HomeDeleted}.
 */
public final class DelHome {

    private final HomeRepository repository;
    private final HomeNotifier notifier;
    private final DomainEventPublisher events;

    public DelHome(HomeRepository repository, HomeNotifier notifier, DomainEventPublisher events) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Delete {@code owner}'s home {@code name}, or reject when no such home exists. */
    public Result<Unit, HomeError> delete(PlayerRef owner, HomeName name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        HomeSet set = repository.load(owner);
        Result<HomeSet.Change, HomeError> outcome = set.delete(name);
        if (outcome.isErr()) {
            HomeError error = outcome.errorOrThrow();
            notifier.send(owner, error.messageKey(), Map.of("home", name.value()));
            return Result.err(error);
        }
        repository.delete(owner, name);
        outcome.orElseThrow().event().ifPresent(events::publish);
        notifier.send(owner, HomesMessageKey.HOME_DELETED, Map.of("home", name.value()));
        return Result.ok();
    }
}
