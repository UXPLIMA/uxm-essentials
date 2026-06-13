package com.uxplima.uxmessentials.homes.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /homeadmin <player> [del|list|tp] [slot]}: full admin management over another player's homes as an
 * explicit verb, audit-logged by the adapter. It reuses the same aggregate transitions the player-facing use
 * cases do — there is no second code path for an admin delete — so the slot/limit invariants hold
 * identically. The acting staff member is the {@code actor} who receives feedback; the {@code target} is the
 * owner whose set is read or changed.
 */
public final class HomeAdmin {

    private final HomeRepository repository;
    private final HomeTeleporter teleporter;
    private final HomeNotifier notifier;
    private final DomainEventPublisher events;

    public HomeAdmin(
            HomeRepository repository, HomeTeleporter teleporter, HomeNotifier notifier, DomainEventPublisher events) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** List {@code target}'s homes to {@code actor}, returning them for the adapter to render. */
    public List<Home> list(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        List<Home> homes = repository.load(target).all();
        notifier.send(
                actor,
                HomesMessageKey.HOME_ADMIN_LIST_HEADER,
                Map.of("player", target.name(), "count", Integer.toString(homes.size())));
        return homes;
    }

    /** Delete {@code target}'s home in {@code slot}, reporting the result to {@code actor}. */
    public Result<Unit, HomeError> delete(PlayerRef actor, PlayerRef target, HomeSlot slot) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(slot, "slot");
        HomeSet set = repository.load(target);
        Result<HomeSet.Change, HomeError> outcome = set.delete(slot);
        if (outcome.isErr()) {
            notifier.send(actor, outcome.errorOrThrow().messageKey(), slotPlaceholder(slot));
            return Result.err(outcome.errorOrThrow());
        }
        repository.deleteSlot(target, slot);
        outcome.orElseThrow().event().ifPresent(events::publish);
        notifier.send(actor, HomesMessageKey.HOME_ADMIN_DELETED, attribution(target, slot));
        return Result.ok();
    }

    /** Teleport {@code actor} to {@code target}'s home in {@code slot}, delegating the hop to teleport. */
    public Result<Unit, HomeError> teleport(PlayerRef actor, PlayerRef target, HomeSlot slot) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(slot, "slot");
        Optional<Home> home = repository.findSlot(target, slot);
        if (home.isEmpty()) {
            notifier.send(actor, HomeError.NOT_FOUND.messageKey(), slotPlaceholder(slot));
            return Result.err(HomeError.NOT_FOUND);
        }
        teleporter.teleportTo(actor, home.get());
        return Result.ok();
    }

    private static Map<String, String> slotPlaceholder(HomeSlot slot) {
        return Map.of("slot", Integer.toString(slot.displayNumber()));
    }

    private static Map<String, String> attribution(PlayerRef target, HomeSlot slot) {
        return Map.of("player", target.name(), "slot", Integer.toString(slot.displayNumber()));
    }
}
