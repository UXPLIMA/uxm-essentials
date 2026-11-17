package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyAudit;
import com.uxplima.uxmessentials.economy.application.port.HistoryRecord;
import com.uxplima.uxmessentials.economy.application.port.KnownPlayers;
import com.uxplima.uxmessentials.economy.application.port.ProviderLane;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SweepMode;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.testing.ManualScheduler;
import com.uxplima.uxmessentials.testing.QuietLog;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import org.junit.jupiter.api.Test;

/**
 * An exact-balance write the store could not apply (a physical balance whose holder's thread never took it) changed
 * nothing, so the admin verb must say it failed and leave no ledger row or audit line behind.
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
    private final List<String> lines = new ArrayList<>();
    private final List<Map<String, String>> placeholders = new ArrayList<>();
    private final EconomyNotifier notifier = new EconomyNotifier(
            (PlayerRef viewer, MessageKey key, Map<String, String> values) -> {
                lines.add(viewer.name() + ":" + key.key());
                placeholders.add(values);
                return key.key();
            },
            (viewer, text) -> {});
    private final EcoAdmin admin = new EcoAdmin(
            bank,
            wallets,
            audit,
            notifier,
            history,
            new SweepRunner(
                    NoSweeps.JOURNAL,
                    new SweepRunner.Paths(
                            currency -> SweepMode.LEDGER,
                            bank,
                            history,
                            currency -> ProviderLane.inPlace(),
                            () -> {},
                            (online, amount, reason) -> new DeliverSweptItems.OnlineGive(0, 0),
                            KnownPlayers.none()),
                    new ManualScheduler(),
                    audit,
                    notifier,
                    new RecordingEvents(),
                    new QuietLog(),
                    CLOCK),
            CLOCK);

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
