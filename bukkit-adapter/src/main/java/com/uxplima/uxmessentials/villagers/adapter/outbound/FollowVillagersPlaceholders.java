package com.uxplima.uxmessentials.villagers.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.adapter.outbound.papi.VillagersPlaceholders;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link VillagersPlaceholders} seam over the live {@link VillagerFollowService}, counting the sessions whose
 * owner is the requesting player. The session map is small by construction (one entry per villager actually
 * walking after somebody, dropped as soon as it dies or is told to stop), so the count is a short in-memory walk.
 */
@NullMarked
public final class FollowVillagersPlaceholders implements VillagersPlaceholders {

    private final VillagerFollowService follows;

    public FollowVillagersPlaceholders(VillagerFollowService follows) {
        this.follows = Objects.requireNonNull(follows, "follows");
    }

    @Override
    public int following(PlayerRef who) {
        return follows.followingCount(Objects.requireNonNull(who, "who").uuid());
    }
}
