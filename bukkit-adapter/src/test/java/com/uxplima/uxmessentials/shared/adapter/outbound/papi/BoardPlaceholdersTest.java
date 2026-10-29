package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import org.junit.jupiter.api.Test;

class BoardPlaceholdersTest {

    private static final PlayerRef READER = new PlayerRef(UUID.randomUUID(), "Reader");
    private static final PlayerRef LEADER = new PlayerRef(UUID.randomUUID(), "Leader");

    // A Wednesday, 22:30 UTC: an hour and a half to midnight, four days and an hour and a half to Monday.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T22:30:00Z"), ZoneOffset.UTC);

    private static final BoardPlaceholders.Words WORDS = (reader, key, values) -> {
        String line = key.key();
        // In key order: a Map.of iterates in an order that changes from one run to the next.
        for (Map.Entry<String, String> value : new java.util.TreeMap<>(values).entrySet()) {
            line = line + " " + value.getKey() + "=" + value.getValue();
        }
        return line;
    };

    private final BoardPlaceholders boards = new BoardPlaceholders(
            PlaceholderContexts.builder().vote(new Votes()).build(), WORDS, CLOCK, ZoneId.of("UTC"));

    @Test
    void aPlaceAnswersItsNameValueAndHead() {
        assertThat(boards.resolve(READER, "top_votes_weekly_1_name")).contains("Leader");
        assertThat(boards.resolve(READER, "top_votes_weekly_1_value")).contains("12");
        assertThat(boards.resolve(READER, "top_votes_weekly_1_head")).contains("Leader");
    }

    @Test
    void aPlaceNobodyHoldsStillAnswers() {
        assertThat(boards.resolve(READER, "top_votes_weekly_2_name")).contains("board.nobody");
        assertThat(boards.resolve(READER, "top_votes_weekly_2_value")).contains("board.none");
        assertThat(boards.resolve(READER, "top_votes_weekly_2_head")).contains(BoardPlaceholders.EMPTY_HEAD);
    }

    @Test
    void theReaderHasTheirOwnRankAndValue() {
        assertThat(boards.resolve(READER, "rank_votes_weekly")).contains("board.none");
        assertThat(boards.resolve(LEADER, "rank_votes_weekly")).contains("1");
        assertThat(boards.resolve(LEADER, "value_votes_weekly")).contains("12");
    }

    @Test
    void aResetIsCountedToTheStartOfTheNextWindow() {
        assertThat(boards.resolve(READER, "resets_daily")).contains("board.resets-hours hours=1 minutes=30");
        assertThat(boards.resolve(READER, "resets_weekly")).contains("board.resets-days days=4 hours=1");
        assertThat(boards.resolve(READER, "resets_alltime")).contains("board.none");
    }

    @Test
    void aBalanceBoardWithNoEconomyAnswersTheDash() {
        assertThat(boards.resolve(READER, "top_balance_alltime_1_name")).contains(PlaceholderResolver.EMPTY);
    }

    @Test
    void whatIsNotABoardFallsThroughToTheOtherKeys() {
        assertThat(boards.resolve(READER, "rank_next")).isEmpty();
        assertThat(boards.resolve(READER, "rank_position")).isEmpty();
        assertThat(boards.resolve(READER, "top_balance_weekly_1_name"))
                .as("a balance has no weekly window")
                .isEmpty();
        assertThat(boards.resolve(READER, "top_votes_weekly_0_name")).isEmpty();
    }

    private static final class Votes implements VotePlaceholders {

        @Override
        public long countFor(PlayerRef who, VotePeriod period) {
            return who.equals(LEADER) ? 12L : 0L;
        }

        @Override
        public int partyCount() {
            return 0;
        }

        @Override
        public int partyThreshold() {
            return 0;
        }

        @Override
        public Optional<VoteRanking> topAt(VotePeriod period, int rank) {
            return rank == 1 ? Optional.of(new VoteRanking(LEADER, 12L)) : Optional.empty();
        }

        @Override
        public OptionalInt positionOf(PlayerRef who, VotePeriod period) {
            return who.equals(LEADER) ? OptionalInt.of(1) : OptionalInt.empty();
        }
    }
}
