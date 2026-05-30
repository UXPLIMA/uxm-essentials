package com.uxplima.uxmessentials.homes.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /renamehome <old> <new>}: rename one of the owner's homes without re-setting it, keeping its
 * location and creation time. The aggregate rejects a missing source ({@link HomeError#NOT_FOUND}) and a
 * target the owner already uses ({@link HomeError#NAME_TAKEN}); the persisted rename is a single keyed
 * row swap so the uniqueness invariant holds at the database too.
 */
public final class RenameHome {

    private final HomeRepository repository;
    private final HomeNotifier notifier;

    public RenameHome(HomeRepository repository, HomeNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Rename {@code owner}'s home {@code from} to {@code to}. */
    public Result<Unit, HomeError> rename(PlayerRef owner, HomeName from, HomeName to) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        HomeSet set = repository.load(owner);
        Result<HomeSet.Change, HomeError> outcome = set.rename(from, to);
        if (outcome.isErr()) {
            HomeError error = outcome.errorOrThrow();
            notifier.send(owner, error.messageKey(), Map.of("home", from.value(), "target", to.value()));
            return Result.err(error);
        }
        repository.rename(owner, from, to);
        notifier.send(owner, HomesMessageKey.HOME_RENAMED, Map.of("home", from.value(), "target", to.value()));
        return Result.ok();
    }
}
