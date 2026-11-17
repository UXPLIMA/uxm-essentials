package com.uxplima.uxmessentials.vote.adapter.outbound;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.OwningThreadReads;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
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
 * {@code 0} never does, which matches {@code RewardSpec}'s percentage semantics. A vote is resolved on the async
 * lane, so whether the voter is online comes from the online index, and their world is read on their own thread.
 */
@NullMarked
public final class BukkitVoteContext implements VoteContext {

    private final Permissions permissions;
    private final PlayerLookup players;
    private final NetworkPresence presence;
    private final OwningThreadReads reads;

    public BukkitVoteContext(
            Permissions permissions, PlayerLookup players, NetworkPresence presence, OwningThreadReads reads) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.players = Objects.requireNonNull(players, "players");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.reads = Objects.requireNonNull(reads, "reads");
    }

    /** The voter's world, or empty when they are offline or their thread could not answer in time. */
    @Override
    public String worldOf(PlayerRef voter) {
        Objects.requireNonNull(voter, "voter");
        return reads.forPlayer(voter, () -> worldNow(voter), "", "");
    }

    private static String worldNow(PlayerRef voter) {
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
        return players.isOnline(voter.uuid());
    }

    /** Online on another backend, as the network presence knows; a memory read. */
    @Override
    public boolean isOnlineElsewhere(PlayerRef voter) {
        Objects.requireNonNull(voter, "voter");
        return presence.find(voter.uuid())
                .map(online -> !online.server().equals(presence.localServer()))
                .orElse(false);
    }
}
