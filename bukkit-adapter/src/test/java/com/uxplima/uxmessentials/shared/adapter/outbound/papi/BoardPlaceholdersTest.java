package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.MoneyFormat;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class BoardPlaceholdersTest {

    private static final PlayerRef READER = new PlayerRef(UUID.randomUUID(), "Reader");
    private static final PlayerRef LEADER = new PlayerRef(UUID.randomUUID(), "Leader");
    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final Money HELD = Money.of(COINS, 1200);

    private static final BoardPlaceholders.Words WORDS = (reader, key, values) -> {
        String line = key.key();
        // In key order: a Map.of iterates in an order that changes from one run to the next.
        for (Map.Entry<String, String> value : new java.util.TreeMap<>(values).entrySet()) {
            line = line + " " + value.getKey() + "=" + value.getValue();
        }
        return line;
    };

    private final BoardPlaceholders boards = new BoardPlaceholders(
            PlaceholderContexts.builder().economy(new Balances()).build(), WORDS);

    @Test
    void aPlaceAnswersItsNameValueAndHead() {
        assertThat(boards.resolve(READER, "top_balance_alltime_1_name")).contains("Leader");
        assertThat(boards.resolve(READER, "top_balance_alltime_1_value")).contains(MoneyFormat.withSymbol(HELD));
        assertThat(boards.resolve(READER, "top_balance_alltime_1_head")).contains("Leader");
    }

    @Test
    void aPlaceNobodyHoldsStillAnswers() {
        assertThat(boards.resolve(READER, "top_balance_alltime_2_name")).contains("board.nobody");
        assertThat(boards.resolve(READER, "top_balance_alltime_2_value")).contains("board.none");
        assertThat(boards.resolve(READER, "top_balance_alltime_2_head")).contains(BoardPlaceholders.EMPTY_HEAD);
    }

    @Test
    void theReaderHasTheirOwnRankAndValue() {
        assertThat(boards.resolve(READER, "rank_balance_alltime")).contains("board.none");
        assertThat(boards.resolve(LEADER, "rank_balance_alltime")).contains("1");
        assertThat(boards.resolve(LEADER, "value_balance_alltime")).contains("1200");
    }

    @Test
    void aBalanceBoardWithNoEconomyAnswersTheDash() {
        BoardPlaceholders bare =
                new BoardPlaceholders(PlaceholderContexts.builder().build(), WORDS);

        assertThat(bare.resolve(READER, "top_balance_alltime_1_name")).contains(PlaceholderResolver.EMPTY);
    }

    @Test
    void whatIsNotABoardFallsThroughToTheOtherKeys() {
        assertThat(boards.resolve(READER, "rank_next")).isEmpty();
        assertThat(boards.resolve(READER, "rank_position")).isEmpty();
        assertThat(boards.resolve(READER, "top_balance_weekly_1_name"))
                .as("a balance has no weekly window")
                .isEmpty();
        assertThat(boards.resolve(READER, "top_balance_alltime_0_name")).isEmpty();
        assertThat(boards.resolve(READER, "top_votes_weekly_1_name"))
                .as("votes answer through the shared boards")
                .isEmpty();
    }

    /** One leader holding 1200 coins. */
    private static final class Balances implements EconomyPlaceholders {

        @Override
        public Money balance(PlayerRef who) {
            return who.equals(LEADER) ? HELD : Money.of(COINS, 0);
        }

        @Override
        public String formatted(PlayerRef who) {
            return who.equals(LEADER) ? "1200" : "0";
        }

        @Override
        public String compact(PlayerRef who) {
            return formatted(who);
        }

        @Override
        public OptionalInt baltopPosition(PlayerRef who) {
            return who.equals(LEADER) ? OptionalInt.of(1) : OptionalInt.empty();
        }

        @Override
        public Optional<Currency> currency(String currencyId) {
            return Optional.of(COINS);
        }

        @Override
        public Currency defaultCurrency() {
            return COINS;
        }

        @Override
        public Money balance(PlayerRef who, Currency currency) {
            return balance(who);
        }

        @Override
        public Optional<BaltopRow> baltopRow(Currency currency, int rank) {
            return rank == 1 ? Optional.of(new BaltopRow(LEADER, HELD)) : Optional.empty();
        }
    }
}
