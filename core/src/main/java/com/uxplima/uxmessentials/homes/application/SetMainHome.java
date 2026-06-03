package com.uxplima.uxmessentials.homes.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.MainHomePreference;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /setmainhome <name>}: record which of an owner's homes plain {@code /home} (no name) teleports
 * to. The choice is validated against the owner's existing homes — a name they have no home under is
 * rejected with {@link HomeError#NOT_FOUND} and nothing is persisted — then stored through
 * {@link MainHomePreference}. This use case never moves the player; it only persists a preference the
 * {@link TeleportHome} resolver consults.
 */
public final class SetMainHome {

    private final HomeRepository repository;
    private final MainHomePreference preferences;
    private final HomeNotifier notifier;

    public SetMainHome(HomeRepository repository, MainHomePreference preferences, HomeNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set {@code owner}'s main home to {@code name}, or reject when they have no such home. */
    public Result<Unit, HomeError> setMain(PlayerRef owner, HomeName name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        HomeSet set = repository.load(owner);
        if (set.find(name).isEmpty()) {
            notifier.send(owner, HomeError.NOT_FOUND.messageKey(), Map.of("home", name.value()));
            return Result.err(HomeError.NOT_FOUND);
        }
        preferences.setMainHome(owner, name);
        notifier.send(owner, HomesMessageKey.HOME_MAIN_SET, Map.of("home", name.value()));
        return Result.ok();
    }
}
