package com.uxplima.uxmessentials.vote.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.ParkedVotes;
import com.uxplima.uxmessentials.vote.domain.ParkedVote;
import com.uxplima.uxmessentials.vote.domain.Vote;
import org.junit.jupiter.api.Test;

/**
 * A vote goes to the account its name belongs to, under the server's case rules, or waits under the name for the
 * player who first joins with it. No account is ever made up from a name.
 */
class ParkedVotesTest {

    private static final Instant AT = Instant.parse("2030-03-01T12:00:00Z");
    private static final PlayerRef STEVE =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000057e"), "Steve");
    private static final PlayerRef BEDROCK_ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0009-01234567890a"), ".Alex");
    private static final PlayerRef BEDROCK_BEA =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0009-0123456789be"), ".Bea");
    private static final PlayerRef NEW_STEVE =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000005e1"), "steve");

    private final Accounts accounts = new Accounts();
    private final KeptVotes parked = new KeptVotes();
    private final List<Vote> credited = new ArrayList<>();
    private final ReceiveNamedVote receive = new ReceiveNamedVote(accounts, parked, () -> ".", credited::add);
    private final BindParkedVotes bind = new BindParkedVotes(parked, credited::add);

    @Test
    void aVoteForAKnownNameIsCreditedToThatAccountAtOnce() {
        accounts.add(STEVE);

        assertThat(receive.receive("Steve", "site-a", AT)).isEqualTo(ReceiveNamedVote.Outcome.CREDITED);

        assertThat(credited).containsExactly(new Vote(STEVE, "site-a", AT));
    }

    @Test
    void aBedrockVoterTypingTheirGamertagWithoutThePrefixIsCreditedToTheirAccount() {
        accounts.add(BEDROCK_ALEX);

        receive.receive("Alex", "site-a", AT);

        assertThat(credited).containsExactly(new Vote(BEDROCK_ALEX, "site-a", AT));
    }

    @Test
    void aVoteForANameNobodyHasIsParkedAndCreditedWhenThatPlayerFirstJoinsWithItsTime() {
        assertThat(receive.receive("Newbie", "site-a", AT)).isEqualTo(ReceiveNamedVote.Outcome.PARKED);
        assertThat(credited).isEmpty();

        PlayerRef newbie = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000ab"), "newbie");
        assertThat(bind.bind(newbie, "")).isEqualTo(1);

        assertThat(credited).containsExactly(new Vote(newbie, "site-a", AT));
        assertThat(bind.bind(newbie, "")).as("credited once").isZero();
    }

    @Test
    void aBedrockPlayerWhoNeverJoinedClaimsTheVoteForTheirGamertagAsTheyFirstJoin() {
        receive.receive("Bea", "site-a", AT);

        bind.bind(BEDROCK_BEA, ".");

        assertThat(credited).containsExactly(new Vote(BEDROCK_BEA, "site-a", AT));
    }

    @Test
    void aBedrockPlayerWhoNeverJoinedClaimsTheVoteForTheirPrefixedNameAsTheyFirstJoin() {
        receive.receive(".Bea", "site-a", AT);

        bind.bind(BEDROCK_BEA, ".");

        assertThat(credited).containsExactly(new Vote(BEDROCK_BEA, "site-a", AT));
    }

    @Test
    void aJavaPlayerDoesNotClaimAVoteThatOnlyMatchesTheirNameWithoutTheBedrockPrefix() {
        receive.receive(".Bea", "site-a", AT);

        bind.bind(new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000be"), "Bea"), "");

        assertThat(credited).isEmpty();
    }

    @Test
    void aNameThatCouldMeanSeveralAccountsWaitsForThatExactSpelling() {
        accounts.ambiguous("steve", STEVE, new PlayerRef(UUID.randomUUID(), "STEVE"));

        receive.receive("steve", "site-a", AT);
        bind.bind(STEVE, "");
        assertThat(credited).as("another spelling is another player").isEmpty();

        bind.bind(NEW_STEVE, "");
        assertThat(credited).containsExactly(new Vote(NEW_STEVE, "site-a", AT));
    }

    @Test
    void aNameNobodyHadClaimsAnyCaseOfIt() {
        receive.receive("steve", "site-a", AT);

        bind.bind(STEVE, "");

        assertThat(credited).containsExactly(new Vote(STEVE, "site-a", AT));
    }

    @Test
    void votesNobodyClaimedForAMonthAreForgotten() {
        receive.receive("Gone", "site-a", AT);

        receive.receive("Other", "site-a", AT.plus(ReceiveNamedVote.KEPT_FOR).plusSeconds(1));
        bind.bind(new PlayerRef(UUID.randomUUID(), "Gone"), "");

        assertThat(credited).isEmpty();
    }

    /** The accounts the server knows, with a name it cannot settle to one of them when told so. */
    private static final class Accounts implements PlayerLookup {

        private final Map<String, PlayerRef> byName = new HashMap<>();
        private final Map<String, List<PlayerRef>> ambiguous = new HashMap<>();

        void add(PlayerRef player) {
            byName.put(player.name(), player);
        }

        void ambiguous(String typed, PlayerRef... variants) {
            ambiguous.put(typed, List.of(variants));
        }

        @Override
        public NameMatch matchByName(String name) {
            List<PlayerRef> variants = ambiguous.get(name);
            if (variants != null) {
                return NameMatch.ambiguous(name, variants);
            }
            return NameMatch.of(Optional.ofNullable(byName.get(name)));
        }

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return byName.values().stream().filter(p -> p.uuid().equals(uuid)).findFirst();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    /** The parked votes, as the database holds them. */
    private static final class KeptVotes implements ParkedVotes {

        private final List<ParkedVote> rows = new ArrayList<>();

        @Override
        public void park(ParkedVote vote) {
            rows.add(vote);
        }

        @Override
        public List<ParkedVote> take(Set<String> names) {
            Set<String> lowered = new HashSet<>();
            names.forEach(name -> lowered.add(name.toLowerCase(Locale.ROOT)));
            List<ParkedVote> taken = new ArrayList<>();
            for (ParkedVote vote : List.copyOf(rows)) {
                boolean named = vote.exactCase()
                        ? names.contains(vote.name())
                        : lowered.contains(vote.name().toLowerCase(Locale.ROOT));
                if (named) {
                    rows.remove(vote);
                    taken.add(vote);
                }
            }
            return taken;
        }

        @Override
        public int forgetOlderThan(Instant cutoff) {
            int before = rows.size();
            rows.removeIf(vote -> vote.at().isBefore(cutoff));
            return before - rows.size();
        }
    }
}
