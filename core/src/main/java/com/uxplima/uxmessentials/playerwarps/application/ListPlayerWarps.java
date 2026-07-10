package com.uxplima.uxmessentials.playerwarps.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /pwarps [player]}: list player-warps. With no argument a player lists their own warps (public and
 * private, in creation order); with a player argument they list only that owner's <em>public</em> warps, so a
 * private warp never leaks to another player. The visible warps are returned for the adapter while the
 * header / per-entry / empty feedback is pushed through the notifier so all text resolves from
 * {@link PlayerwarpsMessageKey}.
 *
 * <p>The own-list filter is exposed side-effect-free as {@link #ownedList(PlayerRef)} for symmetry with the
 * server warps list; {@link #own} delegates to it and then notifies.
 */
public final class ListPlayerWarps {

    private final PlayerWarpRepository repository;
    private final PlayerWarpNotifier notifier;

    public ListPlayerWarps(PlayerWarpRepository repository, PlayerWarpNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** The warps {@code viewer} owns, in stored creation order, with no side effect. */
    public List<PlayerWarp> ownedList(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return repository.ownedBy(viewer);
    }

    /** The warps {@code viewer} owns, also pushing the header/entries (or the empty notice) to them. */
    public List<PlayerWarp> own(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        List<PlayerWarp> owned = ownedList(viewer);
        if (owned.isEmpty()) {
            notifier.send(viewer, PlayerwarpsMessageKey.PWARP_LIST_EMPTY);
            return owned;
        }
        notifier.send(viewer, PlayerwarpsMessageKey.PWARP_LIST_HEADER, Map.of("count", Integer.toString(owned.size())));
        for (PlayerWarp warp : owned) {
            notifier.send(
                    viewer,
                    PlayerwarpsMessageKey.PWARP_LIST_ENTRY,
                    Map.of("warp", warp.name().value()));
        }
        return owned;
    }

    /**
     * The public warps {@code owner} owns, rendered to {@code viewer} under the other-owner header (or the
     * other-owner empty notice). {@code ownerName} is the owner's display name shown in the header.
     */
    public List<PlayerWarp> publicOf(PlayerRef viewer, PlayerRef owner, String ownerName) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(ownerName, "ownerName");
        List<PlayerWarp> shown = repository.publicOwnedBy(owner);
        if (shown.isEmpty()) {
            notifier.send(viewer, PlayerwarpsMessageKey.PWARP_LIST_OTHER_EMPTY, Map.of("player", ownerName));
            return shown;
        }
        notifier.send(
                viewer,
                PlayerwarpsMessageKey.PWARP_LIST_OTHER_HEADER,
                Map.of("player", ownerName, "count", Integer.toString(shown.size())));
        for (PlayerWarp warp : shown) {
            notifier.send(
                    viewer,
                    PlayerwarpsMessageKey.PWARP_LIST_OTHER_ENTRY,
                    Map.of("warp", warp.name().value(), "player", ownerName));
        }
        return shown;
    }
}
