package com.uxplima.uxmessentials.economy.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmResult;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import com.uxplima.uxmessentials.economy.application.EcoAdmin;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyAudit;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Transaction;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published economy actions: money moves through the same admin use case {@code /eco} runs, the audit line
 * names the plugin that asked, and a refusal comes back as a failure a consumer can branch on rather than as an
 * exception they have to catch.
 */
class EconomyActionsTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).symbol("$").precision(2).build();
    private static final Currency GEMS =
            Currency.builder(CurrencyId.of("gems")).symbol("g").precision(0).build();

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");

    private FakeProvider provider;
    private RecordingAudit audit;
    private QueryDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        provider = new FakeProvider();
        audit = new RecordingAudit();
        scheduler = new QueryDoubles.InlineScheduler();
    }

    @Test
    void aDepositLeavesTheMoneyAndAnswersTheNewBalance() {
        provider.set(ALICE, COINS, "10.00");

        UxmResult<UxmMoney> result =
                actions().deposit(ALICE.uuid(), new BigDecimal("15.00")).join();

        assertThat(result.succeeded()).isTrue();
        assertThat(result.valueOrThrow().amount()).isEqualByComparingTo("25.00");
        assertThat(result.valueOrThrow().currency()).isEqualTo("coins");
    }

    @Test
    void aWithdrawalThePlayerCannotCoverChangesNothingAndSaysWhy() {
        provider.set(ALICE, COINS, "5.00");

        UxmResult<UxmMoney> result =
                actions().withdraw(ALICE.uuid(), new BigDecimal("50.00")).join();

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureOrThrow().is(UxmFailure.INSUFFICIENT_FUNDS)).isTrue();
        assertThat(provider.balance(ALICE, COINS).amount())
                .as("a refused withdrawal is not a partial one")
                .isEqualByComparingTo("5.00");
    }

    @Test
    void settingWritesTheExactBalanceAsked() {
        provider.set(ALICE, COINS, "999.00");

        assertThat(actions()
                        .set(ALICE.uuid(), new BigDecimal("1.00"))
                        .join()
                        .valueOrThrow()
                        .amount())
                .isEqualByComparingTo("1.00");
    }

    @Test
    void theAuditLineNamesThePluginThatAsked() {
        actions("MyQuests").deposit(ALICE.uuid(), new BigDecimal("5.00")).join();

        assertThat(audit.actors)
                .as("an operator asking who moved the money gets the plugin's name, not \"the API\"")
                .containsExactly("MyQuests");
    }

    @Test
    void twoPluginsAreTwoDistinctActors() {
        actions("MyQuests").deposit(ALICE.uuid(), BigDecimal.ONE).join();
        actions("MyShop").deposit(ALICE.uuid(), BigDecimal.ONE).join();

        assertThat(audit.actorIds).doesNotHaveDuplicates();
    }

    @Test
    void aNamedCurrencyIsWrittenOnItsOwn() {
        provider.set(ALICE, GEMS, "1");

        actions().deposit(ALICE.uuid(), new BigDecimal("4"), "gems").join();

        assertThat(provider.balance(ALICE, GEMS).amount()).isEqualByComparingTo("5");
        assertThat(provider.balance(ALICE, COINS).amount())
                .as("naming a currency must not fall back to the default one")
                .isEqualByComparingTo("0");
    }

    @Test
    void aCurrencyNobodyConfiguredIsAFailureRatherThanAnException() {
        UxmResult<UxmMoney> result =
                actions().deposit(ALICE.uuid(), BigDecimal.ONE, "doubloons").join();

        assertThat(result.failureOrThrow().is(UxmFailure.NOT_FOUND)).isTrue();
        assertThat(actions()
                        .transfer(ALICE.uuid(), BOB.uuid(), BigDecimal.ONE, "doubloons")
                        .join()
                        .failureOrThrow()
                        .is(UxmFailure.NOT_FOUND))
                .isTrue();
    }

    @Test
    void aTransferMovesBothSides() {
        provider.set(ALICE, COINS, "30.00");
        provider.set(BOB, COINS, "0.00");

        UxmOutcome outcome = actions()
                .transfer(ALICE.uuid(), BOB.uuid(), new BigDecimal("30.00"))
                .join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(provider.balance(ALICE, COINS).amount()).isEqualByComparingTo("0.00");
        assertThat(provider.balance(BOB, COINS).amount()).isEqualByComparingTo("30.00");
    }

    @Test
    void aTransferTheSenderCannotCoverMovesNothing() {
        provider.set(ALICE, COINS, "1.00");
        provider.set(BOB, COINS, "0.00");

        UxmOutcome outcome = actions()
                .transfer(ALICE.uuid(), BOB.uuid(), new BigDecimal("30.00"))
                .join();

        assertThat(outcome.succeeded()).isFalse();
        assertThat(outcome.failureOrThrow().is(UxmFailure.INSUFFICIENT_FUNDS)).isTrue();
        assertThat(provider.balance(BOB, COINS).amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void aRuleTheOperatorSetRefusesRatherThanReportsAShortfall() {
        provider.deny = true;

        assertThat(actions()
                        .transfer(ALICE.uuid(), BOB.uuid(), BigDecimal.ONE)
                        .join()
                        .failureOrThrow()
                        .is(UxmFailure.REFUSED))
                .isTrue();
    }

    @Test
    void aNegativeAmountIsACallerBugAndThrows() {
        assertThatThrownBy(() -> actions().deposit(ALICE.uuid(), new BigDecimal("-1")))
                .as("\"deposit minus fifty\" is a mistake in the caller, not a withdrawal")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> actions().transfer(ALICE.uuid(), BOB.uuid(), new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyWriteRunsOffTheCallingThread() {
        actions().deposit(ALICE.uuid(), BigDecimal.ONE).join();
        actions().transfer(ALICE.uuid(), BOB.uuid(), BigDecimal.ZERO).join();

        assertThat(scheduler.asyncCalls()).isEqualTo(2);
    }

    private EconomyActions actions() {
        return actions("TestPlugin");
    }

    private EconomyActions actions(String source) {
        CurrencyRegistry registry = CurrencyRegistry.of(List.of(COINS, GEMS), COINS.id());
        EcoAdmin admin = new EcoAdmin(
                provider,
                new LedgerRepository(provider),
                audit,
                new EconomyNotifier(new NoMessages(), new NoSink()),
                new NoOpHistory(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        return new EconomyActions(
                admin,
                provider,
                registry,
                new QueryDoubles.MapLookup().with(ALICE).with(BOB),
                scheduler,
                source);
    }

    /** Renders a key as itself; nothing here reads the text, and the API actor is nobody to send it to. */
    private static final class NoMessages implements Messages {

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Feedback addressed to the calling plugin lands nowhere in production, and nowhere here. */
    private static final class NoSink implements MessageSink {

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    /**
     * The ledger the admin verbs write an exact balance through, which {@code set} does. It writes into the same
     * balances the provider reads, because in production they are one store and a test where they are two would
     * agree with itself while the plugin disagreed.
     */
    private static final class LedgerRepository implements WalletRepository {

        private final FakeProvider ledger;

        private LedgerRepository(FakeProvider ledger) {
            this.ledger = ledger;
        }

        @Override
        public Optional<com.uxplima.uxmessentials.economy.domain.Wallet> findByOwner(PlayerRef owner) {
            return Optional.empty();
        }

        @Override
        public com.uxplima.uxmessentials.economy.domain.Wallet ensureOwner(PlayerRef owner) {
            return com.uxplima.uxmessentials.economy.domain.Wallet.empty(owner);
        }

        @Override
        public void upsertBalance(PlayerRef owner, Money balance) {
            ledger.overwrite(owner, balance);
        }

        @Override
        public Result<Unit, TransferError> transfer(PlayerRef from, PlayerRef to, Money amount) {
            throw new AssertionError("the published transfer goes through the provider, not the ledger");
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            throw new AssertionError("the published actions debit through the provider");
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            throw new AssertionError("the published actions credit through the provider");
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            return List.of();
        }

        @Override
        public Result<Unit, TransferError> exchange(PlayerRef owner, Money from, Money to) {
            throw new AssertionError("the published actions do not exchange currencies");
        }
    }

    /** Records nothing: the history rows are EcoAdmin's business and are pinned by its own test. */
    private static final class NoOpHistory implements TransactionHistory {

        @Override
        public List<com.uxplima.uxmessentials.economy.application.port.HistoryRecord> queryTransactions(
                UUID playerUuid, int limit, int offset) {
            return List.of();
        }

        @Override
        public List<com.uxplima.uxmessentials.economy.application.port.HistoryRecord> queryGlobalTransactions(
                int limit, int offset) {
            return List.of();
        }

        @Override
        public void recordTransfer(String fromId, String toId, Money amount, EconomyReason reason, long at) {}

        @Override
        public void recordCredit(String ownerId, Money amount, EconomyReason reason, long at) {}

        @Override
        public void recordDebit(String ownerId, Money amount, EconomyReason reason, long at) {}

        @Override
        public List<com.uxplima.uxmessentials.economy.application.port.HistoryRecord> queryBankTransactions(
                String bankId, int limit, int offset) {
            return List.of();
        }

        @Override
        public void flush() {}
    }

    /** Holds a balance per (player, currency) and moves both legs of a transfer together. */
    private static final class FakeProvider implements EconomyProvider {

        private final Map<String, Money> balances = new LinkedHashMap<>();
        private boolean deny;

        void set(PlayerRef owner, Currency currency, String amount) {
            balances.put(key(owner, currency), Money.of(currency, new BigDecimal(amount)));
        }

        void overwrite(PlayerRef owner, Money balance) {
            balances.put(key(owner, balance.currency()), balance);
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
            balances.putIfAbsent(key(owner, currency), Money.zero(currency));
        }

        @Override
        public Money balance(PlayerRef owner, Currency currency) {
            return balances.getOrDefault(key(owner, currency), Money.zero(currency));
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            Money held = balance(owner, amount.currency());
            balances.put(
                    key(owner, amount.currency()),
                    Money.of(amount.currency(), held.amount().add(amount.amount())));
            return Result.ok();
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            Money held = balance(owner, amount.currency());
            if (held.amount().compareTo(amount.amount()) < 0) {
                return Result.err(TransferError.INSUFFICIENT_FUNDS);
            }
            balances.put(
                    key(owner, amount.currency()),
                    Money.of(amount.currency(), held.amount().subtract(amount.amount())));
            return Result.ok();
        }

        @Override
        public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
            if (deny) {
                return TransferResult.denyWith(
                        com.uxplima.uxmessentials.economy.application.EconomyMessageKey.PAY_SELF);
            }
            Money held = balance(from, amount.currency());
            if (held.amount().compareTo(amount.amount()) < 0) {
                return TransferResult.insufficientFunds(amount, held);
            }
            debit(from, amount);
            credit(to, amount);
            Instant now = Instant.EPOCH;
            return TransferResult.allow(
                    Transaction.debit(from, amount, balance(from, amount.currency()), now),
                    Transaction.credit(to, amount, balance(to, amount.currency()), now));
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            return new ArrayList<>();
        }

        @Override
        public Set<Currency> currencies() {
            return Set.of(COINS, GEMS);
        }
    }

    /** Remembers who each admin write was attributed to, which is the whole point of taking the plugin. */
    private static final class RecordingAudit implements EconomyAudit {

        private final List<String> actors = new ArrayList<>();
        private final List<UUID> actorIds = new ArrayList<>();

        @Override
        public void adminMutation(PlayerRef actor, PlayerRef target, Money amount, EconomyReason reason) {
            actors.add(actor.name());
            actorIds.add(actor.uuid());
        }

        @Override
        public void bulkMutation(PlayerRef actor, Money amount, int affected, EconomyReason reason) {
            actors.add(actor.name());
            actorIds.add(actor.uuid());
        }

        @Override
        public void credited(PlayerRef owner, Money amount, EconomyReason reason) {}

        @Override
        public void debited(PlayerRef owner, Money amount, EconomyReason reason) {}

        @Override
        public void rejected(PlayerRef owner, Money requested, EconomyReason reason) {}

        @Override
        public void transferred(PlayerRef from, PlayerRef to, Money amount, EconomyReason reason) {}

        @Override
        public void worthSet(PlayerRef actor, String material, Money price) {}

        @Override
        public void worthCleared(PlayerRef actor, String material) {}
    }
}
