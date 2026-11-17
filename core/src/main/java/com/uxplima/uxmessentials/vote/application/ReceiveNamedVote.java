package com.uxplima.uxmessentials.vote.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.ParkedVotes;
import com.uxplima.uxmessentials.vote.domain.ParkedVote;
import com.uxplima.uxmessentials.vote.domain.Vote;

/**
 * A vote a vote site sent for a name, credited to the account that name belongs to, or kept until it has one.
 *
 * <p>The name is read through {@link PlayerLookup}, with its case rules: on an offline-mode server a name that only
 * differs in case from several accounts is none of them. A Bedrock player often types their gamertag without the
 * prefix the bridge gives their account, so a name that finds nobody is tried again with that prefix.
 *
 * <p>A name that still finds no single account is never turned into one: an id made up from a name would be the wrong
 * player on a Bedrock bridge, or on an online-mode server for a name Mojang gives someone else. The vote is parked
 * under the name instead and credited, with the time it came, when a player with that name first joins
 * ({@link BindParkedVotes}). When the name could mean several accounts, only that exact spelling claims it. Parked
 * votes nobody claims within {@link #KEPT_FOR} are forgotten.
 *
 * <p>Runs off the tick: the lookup may read the database.
 */
public final class ReceiveNamedVote {

    /** How long a parked vote waits for its player. */
    public static final Duration KEPT_FOR = Duration.ofDays(30);

    private final PlayerLookup players;
    private final ParkedVotes parked;
    private final Supplier<String> bedrockPrefix;
    private final Consumer<Vote> credit;

    /**
     * @param bedrockPrefix the prefix the Bedrock bridge gives its players' names, empty when there is none
     * @param credit the vote use case, which records and rewards a vote for a known account
     */
    public ReceiveNamedVote(
            PlayerLookup players, ParkedVotes parked, Supplier<String> bedrockPrefix, Consumer<Vote> credit) {
        this.players = Objects.requireNonNull(players, "players");
        this.parked = Objects.requireNonNull(parked, "parked");
        this.bedrockPrefix = Objects.requireNonNull(bedrockPrefix, "bedrockPrefix");
        this.credit = Objects.requireNonNull(credit, "credit");
    }

    /** Credit the vote {@code name} cast on {@code serviceName} at {@code at}, or park it; says which. */
    public Outcome receive(String name, String serviceName, Instant at) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(serviceName, "serviceName");
        Objects.requireNonNull(at, "at");
        NameMatch match = players.matchByName(name);
        Optional<PlayerRef> voter = match.player().or(() -> withPrefix(name));
        if (voter.isPresent()) {
            credit.accept(new Vote(voter.get(), serviceName, at));
            return Outcome.CREDITED;
        }
        parked.forgetOlderThan(at.minus(KEPT_FOR));
        parked.park(new ParkedVote(name, serviceName, at, match instanceof NameMatch.Ambiguous));
        return Outcome.PARKED;
    }

    /** The account a Bedrock player typing their gamertag without the bridge's prefix has. */
    private Optional<PlayerRef> withPrefix(String name) {
        String prefix = bedrockPrefix.get();
        if (prefix.isEmpty() || name.startsWith(prefix)) {
            return Optional.empty();
        }
        return players.matchByName(prefix + name).player();
    }

    /** Whether a vote was credited to an account now or parked for a player still to join. */
    public enum Outcome {
        CREDITED,
        PARKED
    }
}
