package com.uxplima.uxmessentials.warps.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;

/**
 * {@code /warps}: list the warps a player may use. The list is filtered to the warps whose per-warp
 * permission node ({@code uxmessentials.warp.use.<warp>}) and optional extra permission the player holds,
 * so a player never sees a warp they cannot teleport to. The visible warps (in creation order) are returned
 * for the adapter to render as a clickable MiniMessage list; the header / per-entry / empty feedback is
 * pushed through the notifier so all text resolves from {@link WarpsMessageKey}.
 */
public final class ListWarps {

    private final WarpRepository repository;
    private final Permissions permissions;
    private final WarpNotifier notifier;

    public ListWarps(WarpRepository repository, Permissions permissions, WarpNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** The warps {@code viewer} may use, also pushing the header/entries (or the empty notice) to them. */
    public List<Warp> list(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        List<Warp> visible =
                repository.all().stream().filter(warp -> canUse(viewer, warp)).toList();
        if (visible.isEmpty()) {
            notifier.send(viewer, WarpsMessageKey.WARP_LIST_EMPTY);
            return visible;
        }
        notifier.send(viewer, WarpsMessageKey.WARP_LIST_HEADER, Map.of("count", Integer.toString(visible.size())));
        for (Warp warp : visible) {
            notifier.send(
                    viewer,
                    WarpsMessageKey.WARP_LIST_ENTRY,
                    Map.of("warp", warp.name().value()));
        }
        return visible;
    }

    private boolean canUse(PlayerRef viewer, Warp warp) {
        if (!permissions.has(viewer, warp.name().useNode())) {
            return false;
        }
        return warp.requiredPermission()
                .map(node -> permissions.has(viewer, node))
                .orElse(true);
    }
}
