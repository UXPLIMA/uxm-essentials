package com.uxplima.uxmessentials.homes.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /homes}: list the owner's homes. Returns the homes (in creation order) for the adapter to render
 * as a clickable MiniMessage list, and sends the header / per-entry / empty feedback through the notifier
 * so all text resolves from {@link HomesMessageKey}. The list itself is a value the adapter formats; this
 * use case owns the read and the feedback, not the click-event wiring.
 *
 * <p>Homes are per-player with no access filter — every home in the owner's own set is theirs to teleport
 * to. The same set is exposed side-effect-free as {@link #homesOf(PlayerRef)} so the {@code /homes} browse
 * menu lists the owner's homes without re-sending the chat feedback; {@link #list} now delegates to it and
 * then notifies.
 */
public final class ListHomes {

    private final HomeRepository repository;
    private final HomeNotifier notifier;

    public ListHomes(HomeRepository repository, HomeNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * The owner's homes, in stored creation order, with no side effect. This is the same set the chat
     * {@link #list} renders; the {@code /homes} browse menu calls this directly so the GUI and the text
     * list never disagree on which homes the owner may teleport to.
     */
    public List<Home> homesOf(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return repository.load(owner).all();
    }

    /** The owner's homes, also pushing the header/entries (or the empty notice) to them. */
    public List<Home> list(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        List<Home> homes = homesOf(owner);
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
