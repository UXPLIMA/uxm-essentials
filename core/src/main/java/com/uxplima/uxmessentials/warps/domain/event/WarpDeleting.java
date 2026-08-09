package com.uxplima.uxmessentials.warps.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.domain.WarpName;

/**
 * A server warp is about to be deleted.
 *
 * @param name which warp
 * @param actor who is removing it
 */
public record WarpDeleting(WarpName name, PlayerRef actor) implements WarpProposal {

    public WarpDeleting {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(actor, "actor");
    }
}
