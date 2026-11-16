package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.AccountSweep;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyAudit;
import com.uxplima.uxmessentials.economy.application.port.HistoryRecord;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import org.junit.jupiter.api.Test;

/**
 * An exact-balance write the store could not apply (a physical balance whose holder's thread never took it) changed
 * nothing, so the admin verb must say it failed and leave no ledger row or audit line behind. The bulk verbs answer
 * with what the sweep over every account committed, and with nothing at all when no sweep ran.
 */
class EcoAdminTest {

    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .starting(new BigDecimal("10"))
            .build();
    private static final PlayerRef ADMIN =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000ad"), "Admin");

    private final InMemoryBank bank = new InMemoryBank(COINS);
    private final Wallets wallets = new Wallets();
    private final Audit audit = new Audit();
    private final History history = new History();
    private final Sweep sweep = new Sweep();
    private final List<String> lines = new ArrayList<>();
    private final List<Map<String, String>> placeholders = new ArrayList<>();
    private final EconomyNotifier notifier = new EconomyNotifier(
            (PlayerRef viewer, MessageKey key, Map<String, String> values) -> {
                lines.add(viewer.name() + ":" + key.key());
                placeholders.add(values);
                return key.key();
            },
            (viewer, text) -> {});
    private final EcoAdmin admin = new EcoAdmin(bank, wallets, audit, notifier, history, sweep, CLOCK);

    private static Money coins(String amount) {
        return Money.of(COINS, new BigDecimal(amount));
    }

    @Test
    void aSetTheStoreAppliedIsRecordedAndAnnounced() {
        bank.set(ALEX, coins("5"));

        Result<Unit, TransferError> result = admin.set(ADMIN, ALEX, coins("50"));

        assertThat(result.isOk()).isTrue();
        assertThat(bank.balance(ALEX, COINS).amount()).isEqualByComparingTo("50");
        assertThat(history.lines).containsExactly("credit:45:ADMIN_SET");
        assertThat(audit.lines).containsExactly("admin:ADMIN_SET");
        assertThat(lines).containsExactly("Admin:" + EconomyMessageKey.ECO_ADMIN_SET.key());
    }

    @Test
    void aSetTheStoreCouldNotApplyFailsAndRecordsNothing() {
        bank.set(ALEX, coins("5"));
        wallets.refuse(ALEX);

        Result<Unit, TransferError> result = admin.set(ADMIN, ALEX, coins("50"));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TransferError.PLAYER_OFFLINE);
        assertThat(bank.balance(ALEX, COINS).amount()).isEqualByComparingTo("5");
        assertThat(history.lines).isEmpty();
        assertThat(audit.lines).isEmpty();
        assertThat(lines)
                .containsExactly(
                        "Admin:" + TransferError.PLAYER_OFFLINE.messageKey().key());
    }

    @Test
    void aResetTheStoreCouldNotApplyFailsAndRecordsNothing() {
        bank.set(ALEX, coins("70"));
        wallets.refuse(ALEX);

        Result<Unit, TransferError> result = admin.reset(ADMIN, ALEX, COINS);

        assertThat(result.isErr()).isTrue();
        assertThat(bank.balance(ALEX, COINS).amount()).isEqualByComparingTo("70");
        assertThat(history.lines).isEmpty();
        assertThat(audit.lines).isEmpty();
    }

    @Test
    void aGiveAllAnswersWithTheAccountsTheSweepCredited() {
        sweep.answer(new AccountSweep.SweepOutcome(1200, 0));

        int affected = admin.giveAll(ADMIN, coins("25"));

        assertThat(affected).isEqualTo(1200);
        assertThat(sweep.calls).containsExactly("credit:25:ADMIN_GIVEALL");
        assertThat(audit.lines).containsExactly("bulk:1200:ADMIN_GIVEALL");
        assertThat(lines).containsExactly("Admin:" + EconomyMessageKey.ECO_ADMIN_GIVEALL.key());
        assertThat(placeholders.getFirst()).containsEntry("count", "1200");
        // The sweep writes each account's ledger row in its own transaction; nothing is buffered beside it.
        assertThat(history.lines).isEmpty();
    }

    @Test
    void aGiveAllSaysHowManyAccountsWereAtTheirLimit() {
        sweep.answer(new AccountSweep.SweepOutcome(40, 3));

        admin.giveAll(ADMIN, coins("25"));

        assertThat(lines)
                .containsExactly(
                        "Admin:" + EconomyMessageKey.ECO_ADMIN_GIVEALL.key(),
                        "Admin:" + EconomyMessageKey.ECO_ADMIN_GIVEALL_CAPPED.key());
        assertThat(placeholders.get(1)).containsEntry("count", "3");
    }

    @Test
    void aResetAllPutsEveryAccountBackToTheStartingBalance() {
        sweep.answer(new AccountSweep.SweepOutcome(7, 0));

        int affected = admin.resetAll(ADMIN, COINS);

        assertThat(affected).isEqualTo(7);
        assertThat(sweep.calls).containsExactly("reset:10:ADMIN_RESETALL");
        assertThat(audit.lines).containsExactly("bulk:7:ADMIN_RESETALL");
        assertThat(lines).containsExactly("Admin:" + EconomyMessageKey.ECO_ADMIN_RESETALL.key());
        assertThat(placeholders.getFirst()).containsEntry("count", "7");
    }

    @Test
    void aCurrencyKeptOutsideTheLedgerIsRefusedAndNothingChanges() {
        sweep.unreachable();

        int affected = admin.giveAll(ADMIN, coins("25"));

        assertThat(affected).isZero();
        assertThat(sweep.calls).isEmpty();
        assertThat(audit.lines).isEmpty();
        assertThat(lines).containsExactly("Admin:" + EconomyMessageKey.ECO_ADMIN_BULK_UNREACHABLE.key());
        assertThat(placeholders.getFirst()).containsEntry("currency", "coins");
    }

    @Test
    void aSecondSweepAskedForWhileOneRunsIsRefused() {
        sweep.answer(new AccountSweep.SweepOutcome(5, 0));
        List<Integer> second = new ArrayList<>();
        sweep.during(() -> second.add(admin.giveAll(ADMIN, coins("25"))));

        int first = admin.giveAll(ADMIN, coins("25"));

        assertThat(first).isEqualTo(5);
        assertThat(second).containsExactly(0);
        assertThat(sweep.calls).hasSize(1);
        assertThat(audit.lines).containsExactly("bulk:5:ADMIN_GIVEALL");
        assertThat(lines)
                .containsExactly(
                        "Admin:" + EconomyMessageKey.ECO_ADMIN_BULK_RUNNING.key(),
                        "Admin:" + EconomyMessageKey.ECO_ADMIN_GIVEALL.key());
    }

    @Test
    void aSweepThatFailedIsReportedAndNeitherAuditedNorAnnouncedAndTheNextOneRuns() {
        sweep.fail(new IllegalStateException("connection lost"));

        assertThatThrownBy(() -> admin.resetAll(ADMIN, COINS)).hasMessage("connection lost");

        assertThat(audit.lines).isEmpty();
        assertThat(lines).containsExactly("Admin:" + EconomyMessageKey.ECO_ADMIN_BULK_FAILED.key());

        sweep.answer(new AccountSweep.SweepOutcome(2, 0));
        assertThat(admin.resetAll(ADMIN, COINS)).isEqualTo(2);
    }

    /** A sweep over every account that answers what it is told to, recording each run. */
    private static final class Sweep implements AccountSweep {

        final List<String> calls = new ArrayList<>();
        private boolean reaches = true;
        private SweepOutcome outcome = SweepOutcome.none();
        private @org.jspecify.annotations.Nullable RuntimeException failure;
        private Runnable during = () -> {};

        void answer(SweepOutcome next) {
            outcome = next;
            failure = null;
        }

        void unreachable() {
            reaches = false;
        }

        void fail(RuntimeException next) {
            failure = next;
        }

        void during(Runnable action) {
            during = action;
        }

        @Override
        public boolean reaches(Currency currency) {
            return reaches;
        }

        @Override
        public SweepOutcome creditEvery(Money amount, EconomyReason reason, long at) {
            return run("credit:" + plain(amount) + ":" + reason);
        }

        @Override
        public SweepOutcome resetEvery(Money balance, EconomyReason reason, long at) {
            return run("reset:" + plain(balance) + ":" + reason);
        }

        private SweepOutcome run(String call) {
            calls.add(call);
            Runnable action = during;
            during = () -> {};
            action.run();
            RuntimeException thrown = failure;
            if (thrown != null) {
                throw thrown;
            }
            return outcome;
        }

        private static String plain(Money money) {
            return money.amount().stripTrailingZeros().toPlainString();
        }
    }

    /** Exact-balance writes land in the bank, except for owners whose holder thread will not take the change. */
    private final class Wallets implements WalletRepository {

        private final Set<PlayerRef> refused = new HashSet<>();

        void refuse(PlayerRef owner) {
            refused.add(owner);
        }

        @Override
        public void upsertBalance(PlayerRef owner, Money balance) {
            if (refused.contains(owner)) {
                throw new IllegalStateException("holder's thread did not take the change");
            }
            bank.set(owner, balance);
        }

        @Override
        public Wallet ensureOwner(PlayerRef owner) {
            return Wallet.empty(owner);
        }

        @Override
        public Optional<Wallet> findByOwner(PlayerRef owner) {
            return Optional.empty();
        }

        @Override
        public Result<Unit, TransferError> transfer(PlayerRef from, PlayerRef to, Money amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, TransferError> exchange(PlayerRef owner, Money debit, Money credit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class Audit implements EconomyAudit {

        final List<String> lines = new ArrayList<>();

        @Override
        public void credited(PlayerRef owner, Money amount, EconomyReason reason) {}

        @Override
        public void debited(PlayerRef owner, Money amount, EconomyReason reason) {}

        @Override
        public void rejected(PlayerRef owner, Money requested, EconomyReason reason) {}

        @Override
        public void transferred(PlayerRef from, PlayerRef to, Money amount, EconomyReason reason) {}

        @Override
        public void adminMutation(PlayerRef actor, PlayerRef target, Money amount, EconomyReason reason) {
            lines.add("admin:" + reason);
        }

        @Override
        public void bulkMutation(PlayerRef actor, Money amount, int affected, EconomyReason reason) {
            lines.add("bulk:" + affected + ":" + reason);
        }

        @Override
        public void worthSet(PlayerRef actor, String material, Money price) {}

        @Override
        public void worthCleared(PlayerRef actor, String material) {}
    }

    private static final class History implements TransactionHistory {

        final List<String> lines = new ArrayList<>();

        @Override
        public void recordCredit(String ownerId, Money amount, EconomyReason reason, long at) {
            lines.add("credit:" + amount.amount().stripTrailingZeros().toPlainString() + ":" + reason);
        }

        @Override
        public void recordDebit(String ownerId, Money amount, EconomyReason reason, long at) {
            lines.add("debit:" + amount.amount().stripTrailingZeros().toPlainString() + ":" + reason);
        }

        @Override
        public void recordTransfer(String fromId, String toId, Money amount, EconomyReason reason, long at) {}

        @Override
        public List<HistoryRecord> queryTransactions(UUID playerUuid, int limit, int offset) {
            return List.of();
        }

        @Override
        public List<HistoryRecord> queryGlobalTransactions(int limit, int offset) {
            return List.of();
        }

        @Override
        public List<HistoryRecord> queryBankTransactions(String bankId, int limit, int offset) {
            return List.of();
        }

        @Override
        public void flush() {}
    }
}
