package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.moderation.application.port.SeenVisibility;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishRecords;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.StoredVanish;
import com.uxplima.uxmessentials.vanish.domain.VanishLevels;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link SeenVisibility} over the vanish context. A player online here is judged by the local vanish store, the
 * authority for who this backend hides. A player on another backend is judged by the vanish row every backend writes
 * to the shared database, which is current whether or not vanish {@code cross-server} is on, unlike the network view.
 * The viewer's see level comes from their permissions here, as it does for everything else they see.
 */
@NullMarked
public final class VanishSeenVisibility implements SeenVisibility {

    private final VanishStore local;
    private final VanishRecords records;
    private final VanishLevelResolver levels;
    private final Predicate<UUID> onlineHere;

    public VanishSeenVisibility(
            VanishStore local, VanishRecords records, VanishLevelResolver levels, Predicate<UUID> onlineHere) {
        this.local = Objects.requireNonNull(local, "local");
        this.records = Objects.requireNonNull(records, "records");
        this.levels = Objects.requireNonNull(levels, "levels");
        this.onlineHere = Objects.requireNonNull(onlineHere, "onlineHere");
    }

    @Override
    public boolean isHiddenFrom(PlayerRef viewer, PlayerRef target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        if (viewer.equals(target)) {
            return false;
        }
        int seeLevel = levels.seeLevel(viewer);
        if (onlineHere.test(target.uuid())) {
            return !local.snapshot().canSee(viewer.uuid(), target.uuid(), seeLevel);
        }
        return records.find(target.uuid())
                .filter(StoredVanish::vanished)
                .flatMap(StoredVanish::level)
                .filter(level -> !VanishLevels.sees(seeLevel, level))
                .isPresent();
    }
}
