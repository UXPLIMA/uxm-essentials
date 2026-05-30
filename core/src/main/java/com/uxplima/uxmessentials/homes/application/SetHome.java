package com.uxplima.uxmessentials.homes.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeLimit;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /sethome [name]}: create a home at the player's current position, or re-anchor an existing one of
 * the same name. The owner's limit is resolved through {@link HomeQuota} scoped to the home's world, the
 * aggregate decides create-vs-move and gates a new name against the cap, and the resulting row is saved.
 * A creation publishes {@code HomeCreated}; a re-anchor saves silently. Hitting the cap publishes
 * {@code HomeLimitReached} and returns {@link HomeError#LIMIT_REACHED} so the command renders the
 * limit message — never an inline literal.
 */
public final class SetHome {

    private final HomeRepository repository;
    private final HomeQuota quota;
    private final HomeNotifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public SetHome(
            HomeRepository repository,
            HomeQuota quota,
            HomeNotifier notifier,
            DomainEventPublisher events,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.quota = Objects.requireNonNull(quota, "quota");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Set or move {@code owner}'s home {@code name} to {@code at}. */
    public Result<Unit, HomeError> set(PlayerRef owner, HomeName name, Position at) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(at, "at");
        HomeSet set = repository.load(owner);
        HomeLimit limit = quota.resolve(owner, at.world());
        boolean creating = set.find(name).isEmpty();
        Result<HomeSet.Change, HomeError> outcome = set.set(name, at, limit, clock.instant());
        if (outcome.isErr()) {
            return reject(set, limit, outcome.errorOrThrow());
        }
        return commit(outcome.orElseThrow(), creating, name);
    }

    private Result<Unit, HomeError> commit(HomeSet.Change change, boolean creating, HomeName name) {
        repository.save(change.home());
        change.event().ifPresent(events::publish);
        HomesMessageKey feedback = creating ? HomesMessageKey.HOME_SET : HomesMessageKey.HOME_MOVED;
        notifier.send(change.home().owner(), feedback, Map.of("home", name.value()));
        return Result.ok();
    }

    private Result<Unit, HomeError> reject(HomeSet set, HomeLimit limit, HomeError error) {
        if (error == HomeError.LIMIT_REACHED) {
            events.publish(set.limitReached(limit));
            notifier.send(set.owner(), error.messageKey(), Map.of("limit", Integer.toString(limit.cap())));
        } else {
            notifier.send(set.owner(), error.messageKey());
        }
        return Result.err(error);
    }
}
