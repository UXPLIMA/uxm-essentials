package com.uxplima.uxmessentials.homes.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /homes}: list the owner's homes. Returns the homes (in creation order) for the adapter to render
 * as a clickable MiniMessage list, and sends the header / per-entry / empty feedback through the notifier
 * so all text resolves from {@link HomesMessageKey}. The list itself is a value the adapter formats; this
 * use case owns the read and the feedback, not the click-event wiring.
 */
public final class ListHomes {

    private final HomeRepository repository;
    private final HomeNotifier notifier;

    public ListHomes(HomeRepository repository, HomeNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** The owner's homes, also pushing the header/entries (or the empty notice) to them. */
    public List<Home> list(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        HomeSet set = repository.load(owner);
        List<Home> homes = set.all();
        if (homes.isEmpty()) {
            notifier.send(owner, HomesMessageKey.HOME_LIST_EMPTY);
            return homes;
        }
        notifier.send(owner, HomesMessageKey.HOME_LIST_HEADER, Map.of("count", Integer.toString(homes.size())));
        for (Home home : homes) {
            notifier.send(
                    owner,
                    HomesMessageKey.HOME_LIST_ENTRY,
                    Map.of("home", home.name().value()));
        }
        return homes;
    }
}
