package com.uxplima.uxmessentials.homes.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeLimit;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.homes.domain.HomesMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * The {@code uxmessentials.home.limit.<world>.<n>} check, asked wherever a home comes into a world: a new home, and a
 * home moved there from another world. Asked only on creation, the limit could be walked past by setting the homes
 * elsewhere and moving them in one by one. The home in the slot being set or moved does not count, so a move inside
 * the world the home is already in always passes as far as the limit goes.
 *
 * <p>Homes are matched by the world's uid, as every position is, so a home left in a world that was reset (a new uid)
 * no longer counts toward that world, only toward the owner's total.
 */
final class HomeWorldLimit {

    private final HomeQuota quota;
    private final Notifier notifier;

    HomeWorldLimit(HomeQuota quota, Notifier notifier) {
        this.quota = Objects.requireNonNull(quota, "quota");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Whether the owner of {@code set} may have the home in {@code slot} in {@code world}. On false the owner has been
     * told the limit and the world.
     */
    boolean admits(HomeSet set, HomeSlot slot, WorldRef world) {
        Optional<HomeLimit> limit = quota.inWorld(set.owner(), world);
        if (limit.isEmpty() || !limit.get().isReachedAt(othersIn(set, slot, world))) {
            return true;
        }
        notifier.send(
                set.owner(),
                HomesMessageKey.HOME_WORLD_LIMIT_REACHED,
                Map.of("limit", Integer.toString(limit.get().cap()), "world", world.name()));
        return false;
    }

    private static int othersIn(HomeSet set, HomeSlot slot, WorldRef world) {
        return Math.toIntExact(set.all().stream()
                .filter(home -> !home.slot().equals(slot))
                .map(Home::location)
                .filter(at -> at.world().equals(world))
                .count());
    }
}
