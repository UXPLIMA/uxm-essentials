package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * A balance that does not answer in time (experience read on the player's thread, a database that is down) shows as
 * empty in a placeholder instead of throwing into the plugin that asked.
 */
class UnansweredBalancePlaceholderTest {

    private static final Currency EXP =
            Currency.builder(CurrencyId.of("exp")).backendId("exp").build();
    private static final PlayerRef ALEX = new PlayerRef(new UUID(0L, 1L), "Alex");

    private final PlaceholderResolver resolver = new PlaceholderResolver(
            PlaceholderContexts.builder().economy(new Unanswered()).build());

    @Test
    void everyBalanceFormShowsEmpty() {
        assertThat(resolver.resolve(ALEX, true, "balance")).contains(PlaceholderResolver.EMPTY);
        assertThat(resolver.resolve(ALEX, true, "economy_balance")).contains(PlaceholderResolver.EMPTY);
        assertThat(resolver.resolve(ALEX, true, "economy_balance_exp")).contains(PlaceholderResolver.EMPTY);
        assertThat(resolver.resolve(ALEX, true, "economy_balance_formatted_exp"))
                .contains(PlaceholderResolver.EMPTY);
    }

    @Test
    void whatDoesNotReadABalanceStillAnswers() {
        assertThat(resolver.resolve(ALEX, true, "economy_currency_symbol")).isPresent();
    }

    /** Every balance read times out. */
    private static final class Unanswered implements EconomyPlaceholders {

        @Override
        public Money balance(PlayerRef who) {
            throw new IllegalStateException("exp balance did not answer");
        }

        @Override
        public String formatted(PlayerRef who) {
            throw new IllegalStateException("exp balance did not answer");
        }

        @Override
        public String compact(PlayerRef who) {
            throw new IllegalStateException("exp balance did not answer");
        }

        @Override
        public OptionalInt baltopPosition(PlayerRef who) {
            return OptionalInt.empty();
        }

        @Override
        public Optional<Currency> currency(String currencyId) {
            return Optional.of(EXP);
        }

        @Override
        public Currency defaultCurrency() {
            return EXP;
        }

        @Override
        public Money balance(PlayerRef who, Currency currency) {
            throw new IllegalStateException("exp balance did not answer");
        }

        @Override
        public Optional<BaltopRow> baltopRow(Currency currency, int rank) {
            return Optional.empty();
        }
    }
}
