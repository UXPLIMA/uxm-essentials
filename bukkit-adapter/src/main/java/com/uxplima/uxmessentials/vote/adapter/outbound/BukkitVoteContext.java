package com.uxplima.uxmessentials.vote.adapter.outbound;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Bukkit {@link VoteContext}: supplies the {@link com.uxplima.uxmessentials.vote.application.RewardEngine}
 * the boundary facts it cannot derive from pure domain. The voter's world and online state are read from the
 * live {@link Player} (an offline voter has no world and is not online), permission checks go through the
 * shared {@link Permissions} port, and the chance roll uses {@link ThreadLocalRandom}.
 *
 * <p>The roll is read as "a {@code 1..100} draw at or below the chance passes": {@code 100} always passes and
 * {@code 0} never does, which matches {@code RewardSpec}'s percentage semantics. The cheap world/online reads
 * happen on whatever thread the engine resolves on (the vote-handling async chain), so they only touch the
 * player lookup, never mutate game state.
 */
@NullMarked
public final class BukkitVoteContext implements VoteContext {

    private final Permissions permissions;

    public BukkitVoteContext(Permissions permissions) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    @Override
    public String worldOf(PlayerRef voter) {
        Objects.requireNonNull(voter, "voter");
        @Nullable Player player = Bukkit.getPlayer(voter.uuid());
        return player == null ? "" : player.getWorld().getName();
    }

    @Override
    public boolean hasPermission(PlayerRef voter, String node) {
        Objects.requireNonNull(voter, "voter");
        Objects.requireNonNull(node, "node");
        return permissions.has(voter, node);
    }

    @Override
    public boolean roll(int chancePercent) {
        // A 100 chance always pays and a 0 chance never does; otherwise draw 1..100 and pass at or below it.
        return chancePercent >= 100
                || (chancePercent > 0 && ThreadLocalRandom.current().nextInt(100) < chancePercent);
    }

    @Override
    public boolean isOnline(PlayerRef voter) {
        Objects.requireNonNull(voter, "voter");
        @Nullable Player player = Bukkit.getPlayer(voter.uuid());
        return player != null;
    }
}
