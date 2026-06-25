package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The subject of an open warp editor: the warp's name, its owner ({@code null} for a server warp, non-null for a
 * player warp), and a display snapshot read off the viewer's entity thread before the open. The editor's
 * placeholders render off this snapshot and its actions re-resolve the live warp through the shared editable-warp
 * loader, so the menu carries no port read of its own. The owner doubles as the server-vs-player discriminator the
 * category button's visibility condition reads.
 *
 * @param warpName the warp's name
 * @param owner the player warp's owner, or {@code null} for a server warp
 * @param display the warp's display projection captured at open
 */
@NullMarked
public record WarpEditTarget(String warpName, @Nullable PlayerRef owner, WarpDisplay display) {

    public WarpEditTarget {
        Objects.requireNonNull(warpName, "warpName");
        Objects.requireNonNull(display, "display");
    }
}
