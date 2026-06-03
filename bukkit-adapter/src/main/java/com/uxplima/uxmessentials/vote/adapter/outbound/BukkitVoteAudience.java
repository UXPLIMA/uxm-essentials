package com.uxplima.uxmessentials.vote.adapter.outbound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import org.jspecify.annotations.NullMarked;

/**
 * The Bukkit {@link VoteAudience}: snapshots the currently online players as {@link PlayerRef}s so a vote
 * party can reward everyone connected and the thank-you can broadcast to them. The snapshot is taken at
 * call time; the use case iterates the returned copy. This is the single place the vote context reads
 * {@code Bukkit.getOnlinePlayers()} — the application asks the port.
 */
@NullMarked
public final class BukkitVoteAudience implements VoteAudience {

    @Override
    public Collection<PlayerRef> online() {
        List<PlayerRef> refs = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            refs.add(BukkitRefs.toRef(player));
        }
        return List.copyOf(refs);
    }
}
