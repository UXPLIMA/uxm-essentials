package com.uxplima.uxmessentials.homes.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /homeadmin <player> [del|list|tp] [name]}: full admin management over another player's homes as
 * an explicit verb, distinct from the per-command {@code .others} nodes and audit-logged by the adapter.
 * It reuses the same aggregate transitions the player-facing use cases do — there is no second code path
 * for an admin delete — so the uniqueness/limit invariants hold identically. The acting staff member is
 * the {@code actor} who receives feedback; the {@code target} is the owner whose set is read or changed.
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

    /** Delete {@code target}'s home {@code name}, reporting the result to {@code actor}. */
    public Result<Unit, HomeError> delete(PlayerRef actor, PlayerRef target, HomeName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(name, "name");
        HomeSet set = repository.load(target);
        Result<HomeSet.Change, HomeError> outcome = set.delete(name);
        if (outcome.isErr()) {
            notifier.send(actor, outcome.errorOrThrow().messageKey(), Map.of("home", name.value()));
            return Result.err(outcome.errorOrThrow());
        }
        repository.delete(target, name);
        outcome.orElseThrow().event().ifPresent(events::publish);
        notifier.send(actor, HomesMessageKey.HOME_ADMIN_DELETED, attribution(target, name));
        return Result.ok();
    }

    /** List {@code target}'s homes to {@code actor}. */
    public List<Home> list(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        List<Home> homes = repository.load(target).all();
        notifier.send(
                actor,
                HomesMessageKey.HOME_ADMIN_LIST_HEADER,
                Map.of("player", target.name(), "count", Integer.toString(homes.size())));
        for (Home home : homes) {
            notifier.send(
                    actor,
                    HomesMessageKey.HOME_LIST_ENTRY,
                    Map.of("home", home.name().value()));
        }
        return homes;
    }

    /** Teleport {@code actor} to {@code target}'s home {@code name}, delegating the hop to teleport. */
    public Result<Unit, HomeError> teleport(PlayerRef actor, PlayerRef target, HomeName name) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(name, "name");
        Optional<Home> home = repository.load(target).find(name);
        if (home.isEmpty()) {
            notifier.send(actor, HomeError.NOT_FOUND.messageKey(), Map.of("home", name.value()));
            return Result.err(HomeError.NOT_FOUND);
        }
        teleporter.teleportTo(actor, home.get());
        return Result.ok();
    }

    private static Map<String, String> attribution(PlayerRef target, HomeName name) {
        return Map.of("player", target.name(), "home", name.value());
    }
}
