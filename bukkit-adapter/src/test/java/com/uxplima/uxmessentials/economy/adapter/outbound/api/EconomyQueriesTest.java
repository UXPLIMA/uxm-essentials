package com.uxplima.uxmessentials.economy.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmBaltopEntry;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import com.uxplima.uxmessentials.economy.adapter.outbound.BaltopSnapshots;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published economy query: balances come from the provider (so a server running Vault answers with that
 * plugin's figures), the leaderboard comes from the same snapshot {@code /baltop} prints, and a currency the
 * operator never configured is an empty answer rather than an exception.
 */
class EconomyQueriesTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).symbol("$").precision(2).build();
    private static final Currency GEMS =
            Currency.builder(CurrencyId.of("gems")).symbol("g").precision(0).build();

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");

    private FakeProvider provider;
    private QueryDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        provider = new FakeProvider();
        scheduler = new QueryDoubles.InlineScheduler();
    }

    @Test
    void theDefaultCurrencyIsListedFirst() {
        assertThat(queries().currencies()).containsExactly("coins", "gems");
    }

    @Test
    void everyBalanceReadRunsOffTheCallingThread() {
        queries().balance(ALICE.uuid()).join();
        queries().balance(ALICE.uuid(), "gems").join();
        queries().balances(ALICE.uuid()).join();

        assertThat(scheduler.asyncCalls()).isEqualTo(3);
    }

    @Test
    void theBalanceIsWhateverTheProviderSays() {
        provider.set(ALICE, COINS, "125.50");

        UxmMoney balance = queries().balance(ALICE.uuid()).join();

        assertThat(balance.currency()).isEqualTo("coins");
        assertThat(balance.amount()).isEqualByComparingTo("125.50");
    }

    @Test
    void aNamedCurrencyIsReadOnItsOwnAndAnUnknownOneIsEmpty() {
        provider.set(ALICE, GEMS, "7");

        assertThat(queries().balance(ALICE.uuid(), "gems").join())
                .hasValueSatisfying(money -> assertThat(money.amount()).isEqualByComparingTo("7"));
        assertThat(queries().balance(ALICE.uuid(), "doubloons").join())
                .as(
                        "which currencies exist is the operator's choice, so asking about one that does not is not an error")
                .isEmpty();
    }

    @Test
    void aCurrencyIdNoCurrencyCouldHaveIsAlsoJustEmpty() {
        assertThat(queries().balance(ALICE.uuid(), "").join()).isEmpty();
        assertThat(queries().top("", 5).join()).isEmpty();
    }

    @Test
    void balancesCoversEveryConfiguredCurrencyInTheOrderCurrenciesLists() {
        provider.set(ALICE, COINS, "10.00");
        provider.set(ALICE, GEMS, "3");

        assertThat(queries().balances(ALICE.uuid()).join())
                .extracting(UxmMoney::currency)
                .containsExactly("coins", "gems");
    }

    @Test
    void affordingIsTrueAtExactlyThePriceAndFalseAPennyShort() {
        provider.set(ALICE, COINS, "10.00");

        assertThat(queries().canAfford(ALICE.uuid(), new BigDecimal("10.00")).join())
                .as("the plugin charges a player who holds exactly the price, so the check has to agree")
                .isTrue();
        assertThat(queries().canAfford(ALICE.uuid(), new BigDecimal("10.01")).join())
                .isFalse();
    }

    @Test
    void affordingIsAskedOfTheNamedCurrencyAndFalseForOneNobodyConfigured() {
        provider.set(ALICE, COINS, "500.00");
        provider.set(ALICE, GEMS, "2");

        assertThat(queries()
                        .canAfford(ALICE.uuid(), new BigDecimal("3"), "gems")
                        .join())
                .as("a rich player is still short on gems, so the currency must not fall back to the default")
                .isFalse();
        assertThat(queries()
                        .canAfford(ALICE.uuid(), new BigDecimal("1"), "gems")
                        .join())
                .isTrue();
        assertThat(queries()
                        .canAfford(ALICE.uuid(), BigDecimal.ZERO, "doubloons")
                        .join())
                .as("nobody holds what does not exist, not even nothing of it")
                .isFalse();
    }

    @Test
    void affordingNothingIsTrueForAPlayerWithNothing() {
        assertThat(queries().canAfford(BOB.uuid(), BigDecimal.ZERO).join()).isTrue();
    }

    @Test
    void aNegativePriceIsRefusedRatherThanAnsweredTrue() {
        assertThatThrownBy(() -> queries().canAfford(ALICE.uuid(), new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queries().canAfford(ALICE.uuid(), new BigDecimal("-1"), "coins"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyAffordabilityCheckRunsOffTheCallingThread() {
        queries().canAfford(ALICE.uuid(), BigDecimal.ONE).join();
        queries().canAfford(ALICE.uuid(), BigDecimal.ONE, "coins").join();

        assertThat(scheduler.asyncCalls()).isEqualTo(2);
    }

    @Test
    void theLeaderboardIsRankedFromOne() {
        provider.set(ALICE, COINS, "100.00");
        provider.set(BOB, COINS, "250.00");

        List<UxmBaltopEntry> top = queries().top(10).join();

        assertThat(top).extracting(UxmBaltopEntry::rank).containsExactly(1, 2);
        assertThat(top).extracting(UxmBaltopEntry::playerName).containsExactly("Bob", "Alice");
        assertThat(top.getFirst().playerId()).isEqualTo(BOB.uuid());
        assertThat(top.getFirst().balance().amount()).isEqualByComparingTo("250.00");
    }

    @Test
    void theLeaderboardIsAnsweredFromMemoryWithoutAHopToAWorkerThread() {
        provider.set(ALICE, COINS, "100.00");

        assertThat(queries().top(10)).isCompleted();
        assertThat(scheduler.asyncCalls())
                .as("the snapshot is already in memory, so answering from it costs no scheduling")
                .isZero();
    }

    @Test
    void aLimitBelowOneIsRefused() {
        assertThatThrownBy(() -> queries().top(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queries().top("coins", -1)).isInstanceOf(IllegalArgumentException.class);
    }

    private EconomyQueries queries() {
        CurrencyRegistry registry = CurrencyRegistry.of(List.of(COINS, GEMS), COINS.id());
        BaltopSnapshots leaderboard = new BaltopSnapshots(
                provider, owner -> false, scheduler, Duration.ofMinutes(5), 10, false, BigDecimal.ZERO, owner -> false);
        return new EconomyQueries(
                provider,
                registry,
                leaderboard,
                new QueryDoubles.MapLookup().with(ALICE).with(BOB),
                scheduler);
    }

    /** Holds a balance per (player, currency) and ranks them the way a real provider would. */
    private static final class FakeProvider implements EconomyProvider {

        private final Map<String, Money> balances = new LinkedHashMap<>();

        void set(PlayerRef owner, Currency currency, String amount) {
            balances.put(key(owner, currency), Money.of(currency, new BigDecimal(amount)));
        }

        private static String key(PlayerRef owner, Currency currency) {
            return owner.uuid() + "|" + currency.id().value();
        }

        @Override
        public boolean hasAccount(PlayerRef owner, Currency currency) {
            return balances.containsKey(key(owner, currency));
        }

        @Override
        public void ensureAccount(PlayerRef owner, Currency currency) {
            throw new AssertionError("a query must never open an account");
        }

        @Override
        public Money balance(PlayerRef owner, Currency currency) {
            return balances.getOrDefault(key(owner, currency), Money.zero(currency));
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            throw new AssertionError("a query must never move money");
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            throw new AssertionError("a query must never move money");
        }

        @Override
        public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
            throw new AssertionError("a query must never move money");
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            List<BaltopRow> rows = new ArrayList<>();
            for (PlayerRef owner : List.of(ALICE, BOB)) {
                Money held = balances.get(key(owner, currency));
                if (held != null) {
                    rows.add(new BaltopRow(owner, held));
                }
            }
            rows.sort((left, right) ->
                    right.balance().amount().compareTo(left.balance().amount()));
            return rows.size() <= limit ? List.copyOf(rows) : List.copyOf(rows.subList(0, limit));
        }

        @Override
        public Set<Currency> currencies() {
            return Set.of(COINS, GEMS);
        }
    }
}
