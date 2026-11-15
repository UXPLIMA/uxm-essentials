package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.RequestVisibility;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link RequestVisibility} over this backend's vanish authority: the see level comes from the viewer's
 * permissions, and a target is hidden when this backend's store has them vanished above the see level the requester's
 * backend sent. Judged by the local store alone, so it holds with vanish {@code cross-server} off.
 */
@NullMarked
public final class VanishRequestVisibility implements RequestVisibility {

    private final VanishStore store;
    private final VanishLevelResolver levels;

    public VanishRequestVisibility(VanishStore store, VanishLevelResolver levels) {
        this.store = Objects.requireNonNull(store, "store");
        this.levels = Objects.requireNonNull(levels, "levels");
    }

    @Override
    public int seeLevel(PlayerRef viewer) {
        return levels.seeLevel(viewer);
    }

    @Override
    public boolean hides(PlayerRef target, PlayerRef viewer, int viewerSeeLevel) {
        return !store.snapshot().canSee(viewer.uuid(), target.uuid(), viewerSeeLevel);
    }
}
