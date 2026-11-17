package com.uxplima.uxmessentials.ranks.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.ranks.application.PrestigeResult.Status;
import com.uxplima.uxmessentials.ranks.application.RanksConfig.PrestigeSettings;
import com.uxplima.uxmessentials.ranks.application.port.OwedCredits;
import com.uxplima.uxmessentials.ranks.application.port.RankActionRunner;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.ranks.application.port.RankRequirementEvaluator;
import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.ranks.domain.RankRequirement;
import com.uxplima.uxmessentials.ranks.domain.RankRequirementType;
import com.uxplima.uxmessentials.ranks.domain.event.PlayerPrestiged;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.ManualScheduler;
import com.uxplima.uxmessentials.testing.QuietLog;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import org.junit.jupiter.api.Test;

class PrestigeTest {

    private static final RankLadder LADDER = RankLadder.of(List.of(
            new Rank(RankId.of("a"), 0, "A", 0L, List.of(), List.of()),
            new Rank(RankId.of("b"), 1, "B", 0L, List.of(), List.of())));
    // The domain level shares its simple name with the use case under test.
    private static final com.uxplima.uxmessentials.ranks.domain.Prestige NEVER =
            com.uxplima.uxmessentials.ranks.domain.Prestige.INITIAL;
    private static final com.uxplima.uxmessentials.ranks.domain.Prestige LEVEL_ONE = NEVER.increment();
    private static final PrestigeSettings SETTINGS = new PrestigeSettings(
            true, 5, 50L, List.of("permission prestige.ok", "playtime 3600"), List.of("message prestiged"), 1.5);

    private final ManualScheduler scheduler = new ManualScheduler();
    private final QuietLog log = new QuietLog();
    private final InMemoryPlayerRanks rows = new InMemoryPlayerRanks();
    private final CachedPlayerRankRepository cache = new CachedPlayerRankRepository(rows, scheduler, log);
    private final Requirements requirements = new Requirements(scheduler);
    private final Actions actions = new Actions();
    private final InMemoryPendingRankActions pending = new InMemoryPendingRankActions();
    private final Purse purse = new Purse();
    private final Owed owed = new Owed();
    private final RecordingEvents events = new RecordingEvents();
    private final Prestige prestige = new Prestige(
            new CurrentRank(cache, LADDER),
            cache,
            LADDER,
            requirements,
            new RankActions(actions, pending, scheduler, log),
            Optional.of(purse),
            Optional.of(owed),
            SETTINGS,
            events,
            scheduler,
            log);

    private void onTopRank(PlayerRef who) {
        rows.put(who.uuid(), "b", 0);
        cache.join(who.uuid());
        scheduler.runAsync();
    }

    @Test
    void aMetPrestigeChargesResetsToTheFirstRankAndRunsItsActions() {
        purse.balance = 100;
        onTopRank(ALEX);

        CompletableFuture<PrestigeResult> result = prestige.prestige(ALEX);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.PRESTIGED);
        assertThat(result.join().newLevel()).isEqualTo(1);
        assertThat(purse.balance).isEqualTo(50);
        assertThat(purse.currencies).containsExactly("withdraw:default");
        assertThat(rows.row(ALEX.uuid())).contains(new PlayerRank(RankId.of("a"), LEVEL_ONE));
        assertThat(actions.ran).containsExactly("message prestiged");
        assertThat(events.published()).containsExactly(new PlayerPrestiged(ALEX, 1, 1.5));
    }

    @Test
    void nothingIsChargedOrSavedOnTheCallersThread() {
        purse.balance = 100;
        onTopRank(ALEX);

        CompletableFuture<PrestigeResult> result = prestige.prestige(ALEX);

        assertThat(result).isNotDone();
        assertThat(purse.balance).isEqualTo(100);
        assertThat(rows.row(ALEX.uuid())).contains(new PlayerRank(RankId.of("b"), NEVER));
    }

    @Test
    void playtimeIsCheckedOnTheAsyncLaneAndTheRestOnTheCallersThread() {
        purse.balance = 100;
        onTopRank(ALEX);

        CompletableFuture<PrestigeResult> result = prestige.prestige(ALEX);
        assertThat(requirements.checkedOnAsync).containsOnlyKeys(RankRequirementType.PERMISSION);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.PRESTIGED);
        assertThat(requirements.checkedOnAsync)
                .containsEntry(RankRequirementType.PERMISSION, false)
                .containsEntry(RankRequirementType.PLAYTIME, true);
    }

    @Test
    void aFailedSaveRefundsTheChargeInTheSameCurrencyAndKeepsTheLevel() {
        purse.balance = 100;
        onTopRank(ALEX);
        rows.failSaves(true);

        CompletableFuture<PrestigeResult> result = prestige.prestige(ALEX);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.FAILED);
        assertThat(purse.balance).isEqualTo(100);
        assertThat(purse.currencies).containsExactly("withdraw:default", "deposit:default");
        assertThat(rows.row(ALEX.uuid())).contains(new PlayerRank(RankId.of("b"), NEVER));
        assertThat(actions.ran).isEmpty();
        assertThat(events.published()).isEmpty();
        assertThat(log.errors()).hasSize(1);
    }

    @Test
    void aRefusedRefundStillAnswersFailedAndIsReported() {
        purse.balance = 100;
        purse.refuseDeposits = true;
        onTopRank(ALEX);
        rows.failSaves(true);

        CompletableFuture<PrestigeResult> result = prestige.prestige(ALEX);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.FAILED);
        assertThat(purse.balance).isEqualTo(50);
        assertThat(log.warnings()).anyMatch(line -> line.startsWith("event=prestige_refund_refused"));
        assertThat(owed.queued).containsExactly("50:default");
    }

    @Test
    void tooLittlePlaytimeRefusesWithoutCharging() {
        purse.balance = 100;
        requirements.storedPass = false;
        onTopRank(ALEX);

        CompletableFuture<PrestigeResult> result = prestige.prestige(ALEX);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.REQUIREMENTS_NOT_MET);
        assertThat(purse.balance).isEqualTo(100);
        assertThat(purse.currencies).isEmpty();
    }

    @Test
    void aShortBalanceNeitherChargesNorResets() {
        purse.balance = 49;
        onTopRank(ALEX);

        CompletableFuture<PrestigeResult> result = prestige.prestige(ALEX);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.CANNOT_AFFORD);
        assertThat(purse.balance).isEqualTo(49);
        assertThat(rows.row(ALEX.uuid())).contains(new PlayerRank(RankId.of("b"), NEVER));
    }

    @Test
    void aSecondPrestigeWhileTheFirstIsSavingIsBusyAndPaysNothing() {
        purse.balance = 200;
        onTopRank(ALEX);

        CompletableFuture<PrestigeResult> first = prestige.prestige(ALEX);
        CompletableFuture<PrestigeResult> second = prestige.prestige(ALEX);
        scheduler.runAsync();

        assertThat(second.join().status()).isEqualTo(Status.BUSY);
        assertThat(first.join().status()).isEqualTo(Status.PRESTIGED);
        assertThat(purse.balance).isEqualTo(150);
    }

    @Test
    void aPlayerBelowTheTopIsRefusedAtOnceWithNothingQueued() {
        purse.balance = 100;
        rows.put(ALEX.uuid(), "a", 0);
        cache.join(ALEX.uuid());
        scheduler.runAsync();

        CompletableFuture<PrestigeResult> result = prestige.prestige(ALEX);

        assertThat(result.join().status()).isEqualTo(Status.NOT_AT_TOP);
        assertThat(scheduler.pendingAsync()).isZero();
    }

    @Test
    void aPlayerWhoLeavesWhileSavingKeepsTheLevelAndItsActionsForTheirNextJoin() {
        purse.balance = 100;
        onTopRank(ALEX);

        CompletableFuture<PrestigeResult> result = prestige.prestige(ALEX);
        scheduler.leave(ALEX);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.PRESTIGED);
        assertThat(actions.ran).isEmpty();
        assertThat(pending.keptFor(ALEX.uuid())).containsExactly("message prestiged");
        assertThat(events.published()).hasSize(1);
    }

    /**
     * Treats playtime as the storage-backed kind, as the real evaluator does, and notes for each kind whether it was
     * checked on the async lane. {@code pass} answers the live kinds, {@code storedPass} the playtime check.
     */
    private static final class Requirements implements RankRequirementEvaluator {

        final Map<RankRequirementType, Boolean> checkedOnAsync = new EnumMap<>(RankRequirementType.class);
        private final ManualScheduler scheduler;
        boolean storedPass = true;
        boolean storedDies;

        Requirements(ManualScheduler scheduler) {
            this.scheduler = scheduler;
        }

        @Override
        public boolean passes(PlayerRef who, RankRequirement requirement) {
            if (storedDies && readsStorage(requirement)) {
                throw new AssertionError("evaluator thread died");
            }
            checkedOnAsync.put(requirement.type(), scheduler.isRunningAsync());
            return !readsStorage(requirement) || storedPass;
        }

        @Override
        public boolean readsStorage(RankRequirement requirement) {
            return requirement.type() == RankRequirementType.PLAYTIME;
        }
    }

    @Test
    void aRefundThatThrowsIsQueuedAsOwed() {
        purse.balance = 100;
        purse.failDeposits = true;
        onTopRank(ALEX);
        rows.failSaves(true);

        CompletableFuture<PrestigeResult> result = prestige.prestige(ALEX);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.FAILED);
        assertThat(owed.queued).containsExactly("50:default");
    }

    @Test
    void aHopTheSchedulerRefusesStillAnswersAndFreesThePlayer() {
        purse.balance = 200;
        onTopRank(ALEX);
        scheduler.shutDown();

        CompletableFuture<PrestigeResult> result = prestige.prestige(ALEX);
        assertThatThrownBy(scheduler::runAsync).isInstanceOf(IllegalStateException.class);

        assertThat(result).isDone();
        assertThat(result.join().status()).isEqualTo(Status.PRESTIGED);
        CompletableFuture<PrestigeResult> next = prestige.prestige(ALEX);
        assertThat(next.isDone() && next.join().status() == Status.BUSY).isFalse();
    }

    @Test
    void anErrorOnTheAsyncLaneStillAnswersAndFreesThePlayer() {
        purse.balance = 200;
        onTopRank(ALEX);
        requirements.storedDies = true;

        CompletableFuture<PrestigeResult> result = prestige.prestige(ALEX);
        assertThatThrownBy(scheduler::runAsync).isInstanceOf(AssertionError.class);

        assertThat(result.join().status()).isEqualTo(Status.FAILED);
        assertThat(purse.balance).isEqualTo(200);
        requirements.storedDies = false;
        CompletableFuture<PrestigeResult> next = prestige.prestige(ALEX);
        scheduler.runAsync();
        assertThat(next.join().status()).isEqualTo(Status.PRESTIGED);
    }

    @Test
    void aSaveThatDiesWithAnErrorRefundsTheChargeAndFreesThePlayer() {
        purse.balance = 100;
        onTopRank(ALEX);
        rows.dieOnSaves(true);

        CompletableFuture<PrestigeResult> result = prestige.prestige(ALEX);
        assertThatThrownBy(scheduler::runAsync).isInstanceOf(AssertionError.class);

        assertThat(result.join().status()).isEqualTo(Status.FAILED);
        assertThat(purse.balance).isEqualTo(100);
        assertThat(actions.ran).isEmpty();
    }

    /** Refunds the economy would not take, queued for the next join as {@code amount:currency}. */
    private static final class Owed implements OwedCredits {

        final List<String> queued = new ArrayList<>();

        @Override
        public void owe(PlayerRef who, BigDecimal amount, String currencyId) {
            queued.add(amount.toPlainString() + ":" + currencyId);
        }
    }

    private static final class Actions implements RankActionRunner {

        final List<String> ran = new ArrayList<>();

        @Override
        public boolean run(PlayerRef who, List<String> actionLines) {
            ran.addAll(actionLines);
            return true;
        }
    }

    /** One player's balance in whole units, with each money move and its currency in order. */
    private static final class Purse implements RankEconomy {

        final List<String> currencies = new ArrayList<>();
        long balance;
        boolean refuseDeposits;
        boolean failDeposits;

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return balance >= amount.longValueExact();
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            long take = amount.longValueExact();
            if (balance < take) {
                return false;
            }
            balance -= take;
            currencies.add("withdraw:" + currencyId);
            return true;
        }

        @Override
        public boolean deposit(PlayerRef who, BigDecimal amount, String currencyId) {
            if (failDeposits) {
                throw new IllegalStateException("economy storage is down");
            }
            if (refuseDeposits) {
                return false;
            }
            balance += amount.longValueExact();
            currencies.add("deposit:" + currencyId);
            return true;
        }
    }
}
