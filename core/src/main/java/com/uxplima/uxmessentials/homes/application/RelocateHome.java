package com.uxplima.uxmessentials.homes.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Re-anchor the home in a slot to the player's current position, keeping its label, icon, and creation
 * time. The aggregate rejects an empty slot with {@link HomeError#NOT_FOUND}; a successful relocate saves
 * the row, publishes {@code HomeRelocated}, and notifies {@link HomesMessageKey#HOME_RELOCATED}.
 */
public final class RelocateHome {

    private final HomeRepository repository;
    private final HomeNotifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public RelocateHome(HomeRepository repository, HomeNotifier notifier, DomainEventPublisher events, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Relocate {@code owner}'s home in {@code slot} to {@code at}, or reject when the slot is empty. */
    public Result<Unit, HomeError> relocate(PlayerRef owner, HomeSlot slot, Position at) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(at, "at");
        HomeSet set = repository.load(owner);
        Result<HomeSet.Change, HomeError> outcome = set.relocate(slot, at, clock.instant());
        if (outcome.isErr()) {
            HomeError error = outcome.errorOrThrow();
            notifier.send(owner, error.messageKey(), slotPlaceholder(slot));
            return Result.err(error);
        }
        HomeSet.Change change = outcome.orElseThrow();
        repository.save(change.home());
        change.event().ifPresent(events::publish);
        notifier.send(owner, HomesMessageKey.HOME_RELOCATED, slotPlaceholder(slot));
        return Result.ok();
    }

    private static Map<String, String> slotPlaceholder(HomeSlot slot) {
        return Map.of("slot", Integer.toString(slot.displayNumber()));
    }
}
