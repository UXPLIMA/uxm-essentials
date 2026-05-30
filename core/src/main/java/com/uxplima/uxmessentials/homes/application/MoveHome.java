package com.uxplima.uxmessentials.homes.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /movehome <name>}: re-anchor an existing home to the player's current position, keeping its name
 * and creation time. Distinct from {@code /sethome} on an existing name only in intent and gating — it
 * requires the home to already exist (rejecting a missing name) and never touches the limit, since the
 * count is unchanged.
 */
public final class MoveHome {

    private final HomeRepository repository;
    private final HomeNotifier notifier;

    public MoveHome(HomeRepository repository, HomeNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Move {@code owner}'s home {@code name} to {@code at}, or reject when no such home exists. */
    public Result<Unit, HomeError> move(PlayerRef owner, HomeName name, Position at) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(at, "at");
        HomeSet set = repository.load(owner);
        Result<HomeSet.Change, HomeError> outcome = set.move(name, at);
        if (outcome.isErr()) {
            HomeError error = outcome.errorOrThrow();
            notifier.send(owner, error.messageKey(), Map.of("home", name.value()));
            return Result.err(error);
        }
        repository.save(outcome.orElseThrow().home());
        notifier.send(owner, HomesMessageKey.HOME_MOVED, Map.of("home", name.value()));
        return Result.ok();
    }
}
