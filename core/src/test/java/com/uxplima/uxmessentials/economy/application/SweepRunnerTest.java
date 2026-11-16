package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import com.uxplima.uxmessentials.economy.domain.SweepKind;
import com.uxplima.uxmessentials.economy.domain.SweepScope;
import com.uxplima.uxmessentials.economy.domain.SweepState;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.economy.domain.event.BalancesSwept;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.testing.ManualScheduler;
import com.uxplima.uxmessentials.testing.QuietLog;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * {@code /eco giveall} and {@code /eco resetall} answer with what the sweep over every account committed, once, by
 * whoever ran its last chunk; a sweep that stopped part way says how far it got and carries on by itself, one the
 * database refuses for good ends there, and an operator can list and cancel the unfinished ones.
 */
class SweepRunnerTest {

    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .starting(new BigDecimal("10"))
            .build();
    private static final PlayerRef ADMIN =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000ad"), "Admin");

    private final Journal journal = new Journal(5);
    private final ManualScheduler scheduler = new ManualScheduler();
    private final Audit audit = new Audit();
    private final RecordingEvents events = new RecordingEvents();
    private final QuietLog log = new QuietLog();
    private final List<String> lines = new ArrayList<>();
    private final List<Map<String, String>> placeholders = new ArrayList<>();
    private final EconomyNotifier notifier = new EconomyNotifier(
            (PlayerRef viewer, MessageKey key, Map<String, String> values) -> {
                lines.add(key.key());
                placeholders.add(values);
                return key.key();
            },
            (viewer, text) -> {});
    private final SweepRunner runner = new SweepRunner(journal, scheduler, audit, notifier, events, log, CLOCK);
    private final InMemoryBank bank = new InMemoryBank(COINS);
    private final EcoAdmin admin = new EcoAdmin(bank, new NoWallets(), audit, notifier, new NoHistory(), runner, CLOCK);

    private static Money coins(String amount) {
        return Money.of(COINS, new BigDecimal(amount));
    }

    @Test
    void aGiveAllAnswersOnceWithEveryAccountItsChunksCredited() {
        journal.accounts(12);

        int affected = admin.giveAll(ADMIN, coins("25"), SweepScope.PLAYERS);

        assertThat(affected).isEqualTo(12);
        assertThat(journal.advances).isEqualTo(4);
        assertThat(journal.opened).containsExactly("CREDIT:PLAYERS:25:ADMIN_GIVEALL");
        assertThat(audit.lines).containsExactly("bulk:12:ADMIN_GIVEALL");
        assertThat(lines).containsExactly(EconomyMessageKey.ECO_ADMIN_GIVEALL.key());
        assertThat(placeholders.getFirst()).containsEntry("count", "12");
        assertThat(events.published()).singleElement().isInstanceOfSatisfying(BalancesSwept.class, swept -> {
            assertThat(swept.kind()).isEqualTo(SweepKind.CREDIT);
            assertThat(swept.accounts()).isEqualTo(12);
            assertThat(swept.amount()).isEqualTo(coins("25"));
        });
    }

    @Test
    void theAccountsLeftOutAreToldApartFromTheAccountsThatWereGone() {
        journal.accounts(4);
        journal.chunkResult(new AccountSweep.SweepOutcome(2, 1, 1));

        admin.giveAll(ADMIN, coins("25"), SweepScope.ALL);

        assertThat(lines)
                .containsExactly(
                        EconomyMessageKey.ECO_ADMIN_GIVEALL.key(),
                        EconomyMessageKey.ECO_ADMIN_GIVEALL_CAPPED.key(),
                        EconomyMessageKey.ECO_ADMIN_BULK_GONE.key());
        assertThat(placeholders.get(1)).containsEntry("count", "1");
        assertThat(placeholders.get(2)).containsEntry("count", "1");
    }

    @Test
    void aResetAllSetsTheStartingBalanceAndSaysHowManyChanged() {
        journal.accounts(3);

        int affected = admin.resetAll(ADMIN, COINS, SweepScope.PLAYERS);

        assertThat(affected).isEqualTo(3);
        assertThat(journal.opened).containsExactly("RESET:PLAYERS:10:ADMIN_RESETALL");
        assertThat(audit.lines).containsExactly("bulk:3:ADMIN_RESETALL");
        assertThat(lines).containsExactly(EconomyMessageKey.ECO_ADMIN_RESETALL.key());
    }

    @Test
    void aCurrencyKeptOutsideTheLedgerIsRefusedAndNothingIsOpened() {
        journal.unreachable();

        int affected = admin.giveAll(ADMIN, coins("25"), SweepScope.PLAYERS);

        assertThat(affected).isZero();
        assertThat(journal.opened).isEmpty();
        assertThat(lines).containsExactly(EconomyMessageKey.ECO_ADMIN_BULK_UNREACHABLE.key());
        assertThat(placeholders.getFirst()).containsEntry("currency", "coins");
    }

    @Test
    void aSweepAskedForWhileTheCurrencyHasAnUnfinishedOneIsRefusedNamingIt() {
        journal.accounts(12);
        journal.failAdvances(2);
        admin.giveAll(ADMIN, coins("25"), SweepScope.PLAYERS);
        lines.clear();
        placeholders.clear();

        // Repeating the command after the pause would pay the first chunk's accounts twice once the first resumed.
        int again = admin.giveAll(ADMIN, coins("25"), SweepScope.PLAYERS);

        assertThat(again).isZero();
        assertThat(journal.opened).hasSize(1);
        assertThat(lines).containsExactly(EconomyMessageKey.ECO_ADMIN_BULK_RUNNING.key());
        assertThat(placeholders.getFirst())
                .containsEntry("id", "sweep-0")
                .containsEntry("currency", "coins")
                .containsEntry("state", "paused");

        scheduler.runAsync();
        assertThat(journal.balanceOfEveryAccount()).isEqualTo(1);
        assertThat(admin.giveAll(ADMIN, coins("25"), SweepScope.PLAYERS)).isEqualTo(12);
    }

    @Test
    void aChunkTheDatabaseRefusesForGoodEndsTheSweepAndSaysHowFarItGot() {
        journal.accounts(12);
        journal.refuseAdvance(2);

        int affected = admin.giveAll(ADMIN, coins("25"), SweepScope.PLAYERS);

        assertThat(affected).isZero();
        assertThat(lines).containsExactly(EconomyMessageKey.ECO_ADMIN_BULK_HALTED.key());
        assertThat(placeholders.getFirst()).containsEntry("count", "5");
        assertThat(audit.lines).containsExactly("bulk:5:ADMIN_GIVEALL");
        assertThat(events.published()).isEmpty();
        assertThat(journal.state("sweep-0")).isEqualTo(SweepState.FAILED);
        // Not paused: nothing is tried again.
        scheduler.runAsync();
        assertThat(lines).hasSize(1);
        assertThat(admin.giveAll(ADMIN, coins("25"), SweepScope.PLAYERS)).isEqualTo(12);
    }

    @Test
    void aPausedSweepIsJournalledAsPausedWithWhatWentWrong() {
        journal.accounts(12);
        journal.failAdvances(2);

        admin.giveAll(ADMIN, coins("25"), SweepScope.PLAYERS);

        assertThat(journal.state("sweep-0")).isEqualTo(SweepState.PAUSED);
        assertThat(journal.error("sweep-0")).contains("connection lost");
    }

    @Test
    void aCancelledSweepKeepsWhatItChangedAndIsNotTriedAgain() {
        journal.accounts(12);
        journal.failAdvances(2);
        admin.giveAll(ADMIN, coins("25"), SweepScope.PLAYERS);
        lines.clear();
        placeholders.clear();

        boolean cancelled = admin.cancelSweep(ADMIN, "sweep-0");

        assertThat(cancelled).isTrue();
        assertThat(lines).containsExactly(EconomyMessageKey.ECO_ADMIN_SWEEP_CANCELLED.key());
        assertThat(placeholders.getFirst()).containsEntry("id", "sweep-0").containsEntry("count", "5");
        assertThat(audit.lines).containsExactly("bulk:5:ADMIN_GIVEALL");
        scheduler.runAsync();
        assertThat(lines).hasSize(1);
        assertThat(journal.changedAccounts()).isEqualTo(5);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void cancellingAnIdNoUnfinishedSweepHasChangesNothing() {
        boolean cancelled = admin.cancelSweep(ADMIN, "nope");

        assertThat(cancelled).isFalse();
        assertThat(lines).containsExactly(EconomyMessageKey.ECO_ADMIN_SWEEP_UNKNOWN.key());
        assertThat(placeholders.getFirst()).containsEntry("id", "nope");
    }

    @Test
    void theUnfinishedSweepsAreListedWithHowFarTheyGotAndTheirLastError() {
        journal.accounts(12);
        journal.failAdvances(2);
        admin.giveAll(ADMIN, coins("25"), SweepScope.ALL);
        lines.clear();
        placeholders.clear();

        admin.listSweeps(ADMIN);

        assertThat(lines)
                .containsExactly(
                        EconomyMessageKey.ECO_ADMIN_SWEEPS_HEADER.key(),
                        EconomyMessageKey.ECO_ADMIN_SWEEPS_ENTRY.key(),
                        EconomyMessageKey.ECO_ADMIN_SWEEPS_ERROR.key());
        assertThat(placeholders.get(1))
                .containsEntry("id", "sweep-0")
                .containsEntry("kind", "giveall")
                .containsEntry("currency", "coins")
                .containsEntry("scope", "all")
                .containsEntry("state", "paused")
                .containsEntry("count", "5")
                .containsEntry("actor", "Admin");
        assertThat(placeholders.get(2).get("error")).contains("connection lost");
    }

    @Test
    void noUnfinishedSweepIsSaidSo() {
        admin.listSweeps(ADMIN);

        assertThat(lines).containsExactly(EconomyMessageKey.ECO_ADMIN_SWEEPS_NONE.key());
    }

    @Test
    void aGiveAllToThePlayersOnlineHereCreditsEachOfThemAndNoOneElse() {
        journal.accounts(12);
        PlayerRef alex = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Alex");
        PlayerRef sam = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000002"), "Sam");

        int affected = admin.giveOnline(ADMIN, List.of(alex, sam), coins("25"));

        assertThat(affected).isEqualTo(2);
        assertThat(bank.balance(alex, COINS)).isEqualTo(coins("25"));
        assertThat(bank.balance(sam, COINS)).isEqualTo(coins("25"));
        assertThat(journal.opened).isEmpty();
        assertThat(audit.lines).containsExactly("bulk:2:ADMIN_GIVEALL");
        assertThat(lines).containsExactly(EconomyMessageKey.ECO_ADMIN_GIVEALL_ONLINE.key());
        assertThat(placeholders.getFirst()).containsEntry("count", "2");
    }

    @Test
    void aChunkThatFailsPausesTheSweepAndALaterTryFinishesItOnce() {
        journal.accounts(12);
        journal.failAdvances(2, 3);

        int affected = admin.giveAll(ADMIN, coins("25"), SweepScope.PLAYERS);

        assertThat(affected).isZero();
        assertThat(audit.lines).isEmpty();
        assertThat(events.published()).isEmpty();
        assertThat(lines).containsExactly(EconomyMessageKey.ECO_ADMIN_BULK_PAUSED.key());
        assertThat(placeholders.getFirst()).containsEntry("count", "5");

        // The first retry fails again and says nothing new; the second finishes.
        scheduler.runAsync();

        assertThat(audit.lines).containsExactly("bulk:12:ADMIN_GIVEALL");
        assertThat(events.published()).hasSize(1);
        assertThat(lines)
                .containsExactly(
                        EconomyMessageKey.ECO_ADMIN_BULK_PAUSED.key(), EconomyMessageKey.ECO_ADMIN_GIVEALL.key());
        assertThat(journal.balanceOfEveryAccount()).isEqualTo(1);
    }

    @Test
    void aSweepLeftRunningByAStopIsCarriedOnAtStartAndAnnouncedOnce() {
        journal.accounts(7);
        AccountSweep.Sweep left = journal.opened(
                SweepKind.CREDIT, SweepScope.PLAYERS, coins("3"), EconomyReason.ADMIN_GIVEALL, ADMIN, 1L);
        journal.advance(left);

        runner.resumeUnfinished();

        assertThat(audit.lines).containsExactly("bulk:7:ADMIN_GIVEALL");
        assertThat(lines).containsExactly(EconomyMessageKey.ECO_ADMIN_GIVEALL.key());
        assertThat(journal.balanceOfEveryAccount()).isEqualTo(1);
        runner.resumeUnfinished();
        assertThat(audit.lines).hasSize(1);
    }

    @Test
    void aSweepAnotherServerFinishedIsNotAnnouncedHere() {
        journal.accounts(3);
        AccountSweep.Sweep elsewhere = journal.opened(
                SweepKind.CREDIT, SweepScope.PLAYERS, coins("3"), EconomyReason.ADMIN_GIVEALL, ADMIN, 1L);
        journal.finishElsewhere(elsewhere);

        assertThat(runner.run(elsewhere)).isNull();

        assertThat(audit.lines).isEmpty();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void aSweepThatCouldNotBeOpenedChangesNothingAndTheNextOneRuns() {
        journal.accounts(2);
        journal.failOpen(new IllegalStateException("database down"));

        assertThatThrownBy(() -> admin.giveAll(ADMIN, coins("25"), SweepScope.PLAYERS))
                .hasMessage("database down");

        assertThat(lines).containsExactly(EconomyMessageKey.ECO_ADMIN_BULK_FAILED.key());
        assertThat(admin.giveAll(ADMIN, coins("25"), SweepScope.PLAYERS)).isEqualTo(2);
    }

    /**
     * The journal and the accounts it walks, in memory: each advance does one chunk of accounts and moves the cursor,
     * all or nothing, the way one transaction does. Like the real journal it holds one unfinished sweep per currency.
     */
    private static final class Journal implements AccountSweep {

        private final int chunk;
        private final List<String> owners = new ArrayList<>();
        private final Map<String, Integer> credits = new LinkedHashMap<>();
        private final Map<String, Entry> entries = new LinkedHashMap<>();
        private final Set<Integer> failing = new HashSet<>();
        private final Set<Integer> refusing = new HashSet<>();
        final List<String> opened = new ArrayList<>();
        int advances;
        private boolean reaches = true;
        private @Nullable SweepOutcome fixedChunk;
        private @Nullable RuntimeException openFailure;

        Journal(int chunk) {
            this.chunk = chunk;
        }

        void accounts(int count) {
            for (int i = 0; i < count; i++) {
                owners.add(String.format("owner-%03d", i));
            }
        }

        void unreachable() {
            reaches = false;
        }

        void chunkResult(SweepOutcome outcome) {
            fixedChunk = outcome;
        }

        void failAdvances(Integer... calls) {
            failing.addAll(List.of(calls));
        }

        void refuseAdvance(int call) {
            refusing.add(call);
        }

        void failOpen(RuntimeException failure) {
            openFailure = failure;
        }

        void finishElsewhere(Sweep sweep) {
            entry(sweep.id()).state = SweepState.DONE;
        }

        SweepState state(String id) {
            return entry(id).state;
        }

        @Nullable String error(String id) {
            return entry(id).error;
        }

        /** Opened straight in the journal, the way a sweep another server or an earlier run opened is found. */
        Sweep opened(SweepKind kind, SweepScope scope, Money amount, EconomyReason reason, PlayerRef actor, long at) {
            return ((Opened) open(kind, scope, amount, reason, actor, at)).sweep();
        }

        /** How many accounts were changed at all. */
        int changedAccounts() {
            return credits.size();
        }

        /** How many times each account was changed, when every account was changed the same number of times. */
        int balanceOfEveryAccount() {
            Set<Integer> distinct = new HashSet<>(credits.values());
            assertThat(credits).hasSize(owners.size());
            assertThat(distinct).hasSize(1);
            return distinct.iterator().next();
        }

        private Entry entry(String id) {
            return java.util.Objects.requireNonNull(entries.get(id));
        }

        @Override
        public boolean reaches(Currency currency) {
            return reaches;
        }

        @Override
        public Opening open(
                SweepKind kind, SweepScope scope, Money amount, EconomyReason reason, PlayerRef actor, long at) {
            RuntimeException failure = openFailure;
            if (failure != null) {
                openFailure = null;
                throw failure;
            }
            for (Entry entry : entries.values()) {
                if (entry.state.unfinished() && entry.sweep.amount().currency().equals(amount.currency())) {
                    return new Busy(entry.status());
                }
            }
            Sweep sweep = new Sweep("sweep-" + entries.size(), kind, scope, amount, reason, actor, at);
            opened.add(kind + ":" + scope + ":"
                    + amount.amount().stripTrailingZeros().toPlainString() + ":" + reason);
            entries.put(sweep.id(), new Entry(sweep));
            return new Opened(sweep);
        }

        @Override
        public Step advance(Sweep sweep) {
            advances++;
            if (failing.remove(advances)) {
                throw new IllegalStateException("connection lost");
            }
            Entry entry = entry(sweep.id());
            if (!entry.state.unfinished()) {
                return new Step(entry.total, List.of(), Progress.ENDED);
            }
            if (refusing.remove(advances)) {
                entry.state = SweepState.FAILED;
                entry.error = "numeric value out of range";
                return new Step(entry.total, List.of(), Progress.FAILED, entry.error);
            }
            entry.state = SweepState.RUNNING;
            if (entry.cursor >= owners.size()) {
                entry.state = SweepState.DONE;
                return new Step(entry.total, List.of(), Progress.FINISHED);
            }
            List<String> slice = owners.subList(entry.cursor, Math.min(owners.size(), entry.cursor + chunk));
            for (String owner : slice) {
                credits.merge(owner, 1, Integer::sum);
            }
            SweepOutcome fixed = fixedChunk;
            entry.total = entry.total.plus(fixed != null ? fixed : new SweepOutcome(slice.size(), 0, 0));
            entry.cursor += slice.size();
            return new Step(entry.total, List.of(), Progress.MORE);
        }

        @Override
        public void pause(Sweep sweep, String error) {
            Entry entry = entry(sweep.id());
            entry.state = SweepState.PAUSED;
            entry.error = error;
        }

        @Override
        public List<Sweep> resumable() {
            return entries.values().stream()
                    .filter(entry -> entry.state.unfinished())
                    .map(entry -> entry.sweep)
                    .toList();
        }

        @Override
        public List<SweepStatus> unfinished() {
            return entries.values().stream()
                    .filter(entry -> entry.state.unfinished())
                    .map(Entry::status)
                    .toList();
        }

        @Override
        public Optional<SweepStatus> cancel(String id, long at) {
            Entry entry = entries.get(id);
            if (entry == null || !entry.state.unfinished()) {
                return Optional.empty();
            }
            entry.state = SweepState.CANCELLED;
            return Optional.of(entry.status());
        }

        private static final class Entry {
            final Sweep sweep;
            int cursor;
            SweepOutcome total = SweepOutcome.none();
            SweepState state = SweepState.RUNNING;

            @Nullable String error;

            Entry(Sweep sweep) {
                this.sweep = sweep;
            }

            SweepStatus status() {
                return new SweepStatus(
                        sweep.id(),
                        sweep.kind(),
                        sweep.scope(),
                        sweep.amount().currency().id().value(),
                        sweep.amount().amount(),
                        sweep.reason(),
                        sweep.actor(),
                        state,
                        total,
                        sweep.startedAt(),
                        error);
            }
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
        public void adminMutation(PlayerRef actor, PlayerRef target, Money amount, EconomyReason reason) {}

        @Override
        public void bulkMutation(PlayerRef actor, Money amount, int affected, EconomyReason reason) {
            lines.add("bulk:" + affected + ":" + reason);
        }

        @Override
        public void worthSet(PlayerRef actor, String material, Money price) {}

        @Override
        public void worthCleared(PlayerRef actor, String material) {}
    }

    /** The single-target verbs are not under test here. */
    private static final class NoWallets implements WalletRepository {

        @Override
        public Optional<Wallet> findByOwner(PlayerRef owner) {
            return Optional.empty();
        }

        @Override
        public Wallet ensureOwner(PlayerRef owner) {
            return Wallet.empty(owner);
        }

        @Override
        public void upsertBalance(PlayerRef owner, Money balance) {
            throw new UnsupportedOperationException();
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
            return List.of();
        }
    }

    private static final class NoHistory implements TransactionHistory {

        @Override
        public List<HistoryRecord> queryTransactions(UUID playerUuid, int limit, int offset) {
            return List.of();
        }

        @Override
        public List<HistoryRecord> queryGlobalTransactions(int limit, int offset) {
            return List.of();
        }

        @Override
        public void recordTransfer(String fromId, String toId, Money amount, EconomyReason reason, long at) {}

        @Override
        public void recordCredit(String ownerId, Money amount, EconomyReason reason, long at) {}

        @Override
        public void recordDebit(String ownerId, Money amount, EconomyReason reason, long at) {}

        @Override
        public List<HistoryRecord> queryBankTransactions(String bankId, int limit, int offset) {
            return List.of();
        }

        @Override
        public void flush() {}
    }
}
