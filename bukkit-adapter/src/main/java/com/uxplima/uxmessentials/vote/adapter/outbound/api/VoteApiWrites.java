package com.uxplima.uxmessentials.vote.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.vote.adapter.VoteServices;
import com.uxplima.uxmessentials.vote.application.AddPartyCount;
import com.uxplima.uxmessentials.vote.application.GiveVote;
import org.jspecify.annotations.NullMarked;

/**
 * The two vote use cases the published API runs.
 *
 * <p>The very instances behind {@code /vote give} and {@code /voteparty add}, so a vote a plugin credits is a
 * vote the streak, the rewards and the party counter all see.
 *
 * @param give {@code /vote give}
 * @param party {@code /voteparty add}
 */
@NullMarked
public record VoteApiWrites(GiveVote give, AddPartyCount party) {

    public VoteApiWrites {
        Objects.requireNonNull(give, "give");
        Objects.requireNonNull(party, "party");
    }

    /** The two as the module built them. */
    public static VoteApiWrites of(VoteServices services) {
        Objects.requireNonNull(services, "services");
        return new VoteApiWrites(services.giveVote(), services.addPartyCount());
    }
}
