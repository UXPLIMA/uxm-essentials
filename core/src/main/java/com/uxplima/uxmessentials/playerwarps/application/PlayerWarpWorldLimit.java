package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpLimit;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * The {@code uxmessentials.pwarp.limit.<world>.<n>} check, asked wherever a warp comes into a world: a new warp, and a
 * warp moved there from another world. Asked only on creation, the limit could be walked past by setting the warps
 * elsewhere and moving them in one by one. A move inside the world the warp is already in adds nothing there, so it
 * always passes.
 *
 * <p>The owner's warps in the world are matched by the world's uid, as every position is, so on a network each backend's
 * world counts apart even when two share a name, and a warp left in a world that was reset (a new uid) no longer counts
 * toward that world, only toward the owner's total.
 */
public final class PlayerWarpWorldLimit {

    private final PlayerWarpQuota quota;
    private final PlayerWarpRepository repository;
    private final Notifier notifier;

    public PlayerWarpWorldLimit(PlayerWarpQuota quota, PlayerWarpRepository repository, Notifier notifier) {
        this.quota = Objects.requireNonNull(quota, "quota");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Whether {@code owner} may have one more warp in {@code world}. On false {@code actor}, the player who asked, has
     * been told the limit and the world.
     */
    public boolean admitsNew(PlayerRef actor, PlayerRef owner, WorldRef world) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(world, "world");
        Optional<PlayerWarpLimit> limit = quota.inWorld(owner, world);
        if (limit.isEmpty() || !limit.get().isReachedAt(ownedIn(owner, world))) {
            return true;
        }
        notifier.send(
                actor,
                PlayerwarpsMessageKey.PWARP_WORLD_LIMIT_REACHED,
                Map.of("limit", Integer.toString(limit.get().cap()), "world", world.name()));
        return false;
    }

    /** Whether {@code warp} may move into {@code to}; a move inside its own world always may. */
    public boolean admitsMove(PlayerRef actor, PlayerWarp warp, WorldRef to) {
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(to, "to");
        return warp.location().world().equals(to) || admitsNew(actor, warp.owner(), to);
    }

    private int ownedIn(PlayerRef owner, WorldRef world) {
        return Math.toIntExact(repository.ownedBy(owner).stream()
                .filter(warp -> warp.location().world().equals(world))
                .count());
    }
}
