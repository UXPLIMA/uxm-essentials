package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.RequestVisibility;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishRecords;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.StoredVanish;
import com.uxplima.uxmessentials.vanish.domain.VanishLevels;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link RequestVisibility} over the vanish context: the see level comes from the viewer's permissions (the
 * console sees everyone), a player here is judged by this backend's store, and a player elsewhere by the vanish row
 * every backend writes to the shared database. Neither depends on vanish {@code cross-server}, so both hold with it
 * off.
 */
@NullMarked
public final class VanishRequestVisibility implements RequestVisibility {

    private final VanishStore store;
    private final VanishRecords records;
    private final VanishLevelResolver levels;

    public VanishRequestVisibility(VanishStore store, VanishRecords records, VanishLevelResolver levels) {
        this.store = Objects.requireNonNull(store, "store");
        this.records = Objects.requireNonNull(records, "records");
        this.levels = Objects.requireNonNull(levels, "levels");
    }

    @Override
    public int seeLevel(PlayerRef viewer) {
        return viewer.isSystem() ? SEES_EVERYONE : levels.seeLevel(viewer);
    }

    @Override
    public boolean hides(PlayerRef target, PlayerRef viewer, int viewerSeeLevel) {
        return !store.snapshot().canSee(viewer.uuid(), target.uuid(), viewerSeeLevel);
    }

    @Override
    public boolean hiddenElsewhere(PlayerRef target, int viewerSeeLevel) {
        return records.find(target.uuid())
                .filter(StoredVanish::vanished)
                .map(StoredVanish::levelOrDefault)
                .filter(level -> !VanishLevels.sees(viewerSeeLevel, level))
                .isPresent();
    }
}
