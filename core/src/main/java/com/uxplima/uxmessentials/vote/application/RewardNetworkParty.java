package com.uxplima.uxmessentials.vote.application;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.RewardApplier;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.domain.reward.RewardGrant;

/**
 * Pay this backend's online players for a vote party that fired on another backend. The backend that fired
 * the party paid only the players it had online, so without this a player on any other backend of the network
 * missed the reward.
 *
 * <p>A player is connected to one backend at a time, so each backend rewarding only its own players pays every
 * eligible player exactly once. The backend that fired never receives its own frame (the bus drops self-origin
 * frames), so it does not pay twice. Who is eligible is decided by the backend that fired: everyone, or only
 * the voters of the party window it lists. The reward itself is this backend's configured party reward, and
 * nothing happens while {@link PartyConfig#networkRewards()} is off.
 */
public final class RewardNetworkParty {

    private final RewardApplier applier;
    private final VoteAudience audience;
    private final PartyConfig party;

    public RewardNetworkParty(RewardApplier applier, VoteAudience audience, PartyConfig party) {
        this.applier = Objects.requireNonNull(applier, "applier");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.party = Objects.requireNonNull(party, "party");
    }

    /** Reward every player online here. */
    public void rewardEveryone() {
        reward(player -> true);
    }

    /** Reward the players online here who are among {@code voters}. */
    public void rewardVoters(Set<UUID> voters) {
        Set<UUID> eligible = Set.copyOf(Objects.requireNonNull(voters, "voters"));
        reward(player -> eligible.contains(player.uuid()));
    }

    private void reward(Predicate<PlayerRef> eligible) {
        if (!party.networkRewards()) {
            return;
        }
        RewardGrant grant = RewardGrant.of(party.reward());
        for (PlayerRef player : audience.online()) {
            if (eligible.test(player)) {
                applier.apply(player, true, grant);
            }
        }
    }
}
