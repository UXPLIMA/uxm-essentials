package com.uxplima.uxmessentials.vote.application;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.ParkedVotes;
import com.uxplima.uxmessentials.vote.domain.ParkedVote;
import com.uxplima.uxmessentials.vote.domain.Vote;

/**
 * Credits a joining player with the votes parked for their name ({@link ReceiveNamedVote}), each with the time it
 * came. A Bedrock player also claims the votes cast for their gamertag without the bridge's prefix. Taking a parked
 * vote removes it, so it is credited once, on one server.
 *
 * <p>Runs off the tick: it reads and writes the database.
 */
public final class BindParkedVotes {

    private final ParkedVotes parked;
    private final Consumer<Vote> credit;

    public BindParkedVotes(ParkedVotes parked, Consumer<Vote> credit) {
        this.parked = Objects.requireNonNull(parked, "parked");
        this.credit = Objects.requireNonNull(credit, "credit");
    }

    /**
     * Credit {@code joiner} with the votes parked for their name.
     *
     * @param bedrockPrefix the prefix the bridge gave {@code joiner}'s name, or empty for a Java player
     * @return how many votes were credited
     */
    public int bind(PlayerRef joiner, String bedrockPrefix) {
        Objects.requireNonNull(joiner, "joiner");
        Objects.requireNonNull(bedrockPrefix, "bedrockPrefix");
        Set<String> names = new LinkedHashSet<>();
        names.add(joiner.name());
        if (!bedrockPrefix.isEmpty()
                && joiner.name().startsWith(bedrockPrefix)
                && joiner.name().length() > bedrockPrefix.length()) {
            names.add(joiner.name().substring(bedrockPrefix.length()));
        }
        int credited = 0;
        for (ParkedVote vote : parked.take(names)) {
            credit.accept(new Vote(joiner, vote.serviceName(), vote.at()));
            credited++;
        }
        return credited;
    }
}
