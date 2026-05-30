package com.uxplima.uxmessentials.homes.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /home [name]}: teleport the player to one of their homes. With no name the owner's default
 * (first-created) home is used; with a name that home is resolved. Execution is <em>delegated</em> to the
 * teleport context through {@link HomeTeleporter} — this use case never moves the player itself, so the
 * shared cooldown, the move-cancellable warmup, and the region-aware async hop are all the teleport
 * context's concern. A missing home or an empty set is rejected with the matching {@link HomeError}.
 */
public final class TeleportHome {

    private final HomeRepository repository;
    private final HomeTeleporter teleporter;
    private final HomeNotifier notifier;

    public TeleportHome(HomeRepository repository, HomeTeleporter teleporter, HomeNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** {@code /home}: teleport {@code who} to their default home, or reject when they have none. */
    public Result<Unit, HomeError> toDefault(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        HomeSet set = repository.load(who);
        Optional<Home> home = set.defaultHome();
        if (home.isEmpty()) {
            notifier.send(who, HomeError.NONE_SET.messageKey());
            return Result.err(HomeError.NONE_SET);
        }
        return go(who, home.get());
    }

    /** {@code /home <name>}: teleport {@code who} to their named home, or reject when it is missing. */
    public Result<Unit, HomeError> toNamed(PlayerRef who, HomeName name) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        Optional<Home> home = repository.load(who).find(name);
        if (home.isEmpty()) {
            notifier.send(who, HomeError.NOT_FOUND.messageKey(), Map.of("home", name.value()));
            return Result.err(HomeError.NOT_FOUND);
        }
        return go(who, home.get());
    }

    /** {@code /home <player>:<name>}: teleport {@code who} to another owner's home (staff). */
    public Result<Unit, HomeError> toOther(PlayerRef who, PlayerRef owner, HomeName name) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Optional<Home> home = repository.load(owner).find(name);
        if (home.isEmpty()) {
            notifier.send(who, HomeError.NOT_FOUND.messageKey(), Map.of("home", name.value()));
            return Result.err(HomeError.NOT_FOUND);
        }
        return go(who, home.get());
    }

    private Result<Unit, HomeError> go(PlayerRef who, Home home) {
        notifier.send(
                who,
                HomesMessageKey.HOME_TELEPORTING,
                Map.of("home", home.name().value()));
        teleporter.teleportTo(who, home);
        return Result.ok();
    }
}
