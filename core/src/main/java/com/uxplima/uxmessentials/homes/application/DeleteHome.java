package com.uxplima.uxmessentials.homes.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.homes.domain.event.HomeDeleting;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /delhome}: remove the home in a slot, freeing it under the owner's limit. An empty slot is
 * rejected with {@link HomeError#NOT_FOUND}; a successful delete removes the row and publishes
 * {@code HomeDeleted}.
 */
public final class DeleteHome {

    private final HomeRepository repository;
    private final HomeInviteRepository invites;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final DomainGate gate;

    public DeleteHome(
            HomeRepository repository,
            HomeInviteRepository invites,
            Notifier notifier,
            DomainEventPublisher events,
            DomainGate gate) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.invites = Objects.requireNonNull(invites, "invites");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    /** Delete {@code owner}'s home in {@code slot}, or reject when the slot is empty. */
    public Result<Unit, HomeError> delete(PlayerRef owner, HomeSlot slot) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(slot, "slot");
        HomeSet set = repository.load(owner);
        Result<HomeSet.Change, HomeError> outcome = set.delete(slot);
        if (outcome.isErr()) {
            HomeError error = outcome.errorOrThrow();
            notifier.send(owner, error.messageKey(), slotPlaceholder(slot));
            return Result.err(error);
        }
        // The home is known to exist and be deletable; asking here means a refusal costs nothing to undo.
        if (!gate.allows(new HomeDeleting(owner, slot))) {
            notifier.send(owner, HomeError.VETOED.messageKey(), slotPlaceholder(slot));
            return Result.err(HomeError.VETOED);
        }
        repository.deleteSlot(owner, slot);
        invites.removeAll(owner, slot);
        outcome.orElseThrow().event().ifPresent(events::publish);
        notifier.send(owner, HomesMessageKey.HOME_DELETED, slotPlaceholder(slot));
        return Result.ok();
    }

    private static Map<String, String> slotPlaceholder(HomeSlot slot) {
        return Map.of("slot", Integer.toString(slot.displayNumber()));
    }
}
