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

import com.uxplima.uxmessentials.ranks.application.RankupResult.Status;
import com.uxplima.uxmessentials.ranks.application.port.OwedCredits;
import com.uxplima.uxmessentials.ranks.application.port.RankActionRunner;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.ranks.application.port.RankRequirementEvaluator;
import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.ranks.domain.RankRequirement;
import com.uxplima.uxmessentials.ranks.domain.RankRequirementType;
import com.uxplima.uxmessentials.ranks.domain.event.PlayerRankedUp;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.ManualScheduler;
import com.uxplima.uxmessentials.testing.QuietLog;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import org.junit.jupiter.api.Test;

class RankupTest {

    private static final RankLadder LADDER = RankLadder.of(List.of(
            new Rank(RankId.of("a"), 0, "A", 0L, List.of(), List.of()),
            new Rank(
                    RankId.of("b"),
                    1,
                    "B",
                    40L,
                    List.of("permission rank.b", "playtime 3600"),
                    List.of("message welcome to b"))));

    private final ManualScheduler scheduler = new ManualScheduler();
    private final QuietLog log = new QuietLog();
    private final InMemoryPlayerRanks rows = new InMemoryPlayerRanks();
    private final CachedPlayerRankRepository cache = new CachedPlayerRankRepository(rows, scheduler, log);
    private final Requirements requirements = new Requirements(scheduler);
    private final Actions actions = new Actions();
    private final InMemoryPendingRankActions pending = new InMemoryPendingRankActions();
    private final RankActions rankActions = new RankActions(actions, pending, scheduler, log);
    private final Purse purse = new Purse();
    private final Owed owed = new Owed();
    private final RecordingEvents events = new RecordingEvents();
    private final Rankup rankup = new Rankup(
            new CurrentRank(cache, LADDER),
            cache,
            LADDER,
            requirements,
            rankActions,
            Optional.of(purse),
            Optional.of(owed),
            events,
            scheduler,
            log);

    private void online(PlayerRef who) {
        cache.join(who.uuid());
        scheduler.runAsync();
    }

    @Test
    void aMetRankupChargesTheCostSavesTheRankAndRunsItsActions() {
        purse.balance = 100;
        online(ALEX);

        CompletableFuture<RankupResult> result = rankup.rankUp(ALEX);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.RANKED_UP);
        assertThat(purse.balance).isEqualTo(60);
        assertThat(rows.row(ALEX.uuid())).contains(new PlayerRank(RankId.of("b"), Prestige.INITIAL));
        assertThat(actions.ran).containsExactly("message welcome to b");
        assertThat(events.published()).containsExactly(new PlayerRankedUp(ALEX, RankId.of("a"), RankId.of("b")));
    }

    @Test
    void nothingIsChargedOrSavedUntilTheAsyncLaneRuns() {
        purse.balance = 100;
        online(ALEX);

        CompletableFuture<RankupResult> result = rankup.rankUp(ALEX);

        assertThat(result).isNotDone();
        assertThat(purse.balance).isEqualTo(100);
        assertThat(rows.row(ALEX.uuid())).isEmpty();
    }

    @Test
    void aFailedSaveRefundsTheChargeAndLeavesTheRank() {
        purse.balance = 100;
        online(ALEX);
        rows.failSaves(true);

        CompletableFuture<RankupResult> result = rankup.rankUp(ALEX);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.FAILED);
        assertThat(purse.balance).isEqualTo(100);
        assertThat(purse.withdrawn).isEqualTo(40);
        assertThat(rows.row(ALEX.uuid())).isEmpty();
        assertThat(cache.cached(ALEX.uuid())).contains(Optional.empty());
        assertThat(log.errors()).hasSize(1);
        assertThat(actions.ran).isEmpty();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void aRefundThatIsRefusedStillAnswersFailedAndIsReported() {
        purse.balance = 100;
        purse.refuseDeposits = true;
        online(ALEX);
        rows.failSaves(true);

        CompletableFuture<RankupResult> result = rankup.rankUp(ALEX);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.FAILED);
        assertThat(purse.balance).isEqualTo(60);
        assertThat(log.errors()).hasSize(1);
        assertThat(log.warnings()).anyMatch(line -> line.startsWith("event=rankup_refund_refused"));
        assertThat(owed.queued).containsExactly("40:default");
    }

    @Test
    void playtimeIsCheckedOnTheAsyncLaneAndTheRestOnTheCallersThread() {
        purse.balance = 100;
        online(ALEX);

        CompletableFuture<RankupResult> result = rankup.rankUp(ALEX);
        assertThat(requirements.checkedOnAsync).containsOnlyKeys(RankRequirementType.PERMISSION);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.RANKED_UP);
        assertThat(requirements.checkedOnAsync)
                .containsEntry(RankRequirementType.PERMISSION, false)
                .containsEntry(RankRequirementType.PLAYTIME, true);
    }

    @Test
    void tooLittlePlaytimeRefusesWithoutCharging() {
        purse.balance = 100;
        requirements.storedPass = false;
        online(ALEX);

        CompletableFuture<RankupResult> result = rankup.rankUp(ALEX);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.REQUIREMENTS_NOT_MET);
        assertThat(purse.balance).isEqualTo(100);
        assertThat(rows.row(ALEX.uuid())).isEmpty();
    }

    @Test
    void theNextRankupAfterARefusalIsNotBusy() {
        purse.balance = 100;
        requirements.pass = false;
        online(ALEX);
        assertThat(rankup.rankUp(ALEX).join().status()).isEqualTo(Status.REQUIREMENTS_NOT_MET);
        requirements.pass = true;

        CompletableFuture<RankupResult> result = rankup.rankUp(ALEX);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.RANKED_UP);
    }

    @Test
    void theNextRankupAfterACompletedOneIsNotBusy() {
        purse.balance = 100;
        online(ALEX);
        CompletableFuture<RankupResult> first = rankup.rankUp(ALEX);
        scheduler.runAsync();
        assertThat(first.join().status()).isEqualTo(Status.RANKED_UP);

        assertThat(rankup.rankUp(ALEX).join().status()).isEqualTo(Status.ALREADY_MAX);
    }

    @Test
    void aShortBalanceNeitherChargesNorAdvances() {
        purse.balance = 39;
        online(ALEX);

        CompletableFuture<RankupResult> result = rankup.rankUp(ALEX);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.CANNOT_AFFORD);
        assertThat(purse.balance).isEqualTo(39);
        assertThat(rows.row(ALEX.uuid())).isEmpty();
    }

    @Test
    void unmetRequirementsAnswerAtOnceWithNothingQueued() {
        purse.balance = 100;
        requirements.pass = false;
        online(ALEX);

        CompletableFuture<RankupResult> result = rankup.rankUp(ALEX);

        assertThat(result.join().status()).isEqualTo(Status.REQUIREMENTS_NOT_MET);
        assertThat(scheduler.pendingAsync()).isZero();
        assertThat(purse.balance).isEqualTo(100);
    }

    @Test
    void aSecondRankupWhileTheFirstIsSavingIsBusyAndPaysNothing() {
        purse.balance = 100;
        online(ALEX);

        CompletableFuture<RankupResult> first = rankup.rankUp(ALEX);
        CompletableFuture<RankupResult> second = rankup.rankUp(ALEX);
        scheduler.runAsync();

        assertThat(second.join().status()).isEqualTo(Status.BUSY);
        assertThat(first.join().status()).isEqualTo(Status.RANKED_UP);
        assertThat(purse.balance).isEqualTo(60);
    }

    @Test
    void aRankupBeforeThePointerLoadsIsBusy() {
        purse.balance = 100;
        cache.join(ALEX.uuid());

        CompletableFuture<RankupResult> result = rankup.rankUp(ALEX);

        assertThat(result.join().status()).isEqualTo(Status.BUSY);
        assertThat(purse.balance).isEqualTo(100);
    }

    @Test
    void aFreeAutorankStepAdvancesWithoutCharging() {
        purse.balance = 100;
        online(ALEX);

        CompletableFuture<RankupResult> result = rankup.rankUp(ALEX, false);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.RANKED_UP);
        assertThat(purse.balance).isEqualTo(100);
        assertThat(rows.row(ALEX.uuid())).contains(new PlayerRank(RankId.of("b"), Prestige.INITIAL));
    }

    @Test
    void aPlayerWhoLeavesWhileSavingKeepsTheRankAndGetsItsActionsOnTheirNextJoin() {
        purse.balance = 100;
        online(ALEX);

        CompletableFuture<RankupResult> result = rankup.rankUp(ALEX);
        scheduler.leave(ALEX);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.RANKED_UP);
        assertThat(rows.row(ALEX.uuid())).contains(new PlayerRank(RankId.of("b"), Prestige.INITIAL));
        assertThat(actions.ran).isEmpty();
        assertThat(pending.keptFor(ALEX.uuid())).containsExactly("message welcome to b");
        assertThat(events.published()).hasSize(1);

        scheduler.arrive(ALEX);
        rankActions.joined(ALEX);
        scheduler.runAsync();

        assertThat(actions.ran).containsExactly("message welcome to b");
        assertThat(pending.keptFor(ALEX.uuid())).isEmpty();
    }

    /**
     * Treats playtime as the storage-backed kind, as the real evaluator does, and notes for each kind whether it was
     * checked on the async lane. {@code pass} answers the live kinds, {@code storedPass} the playtime check.
     */
    private static final class Requirements implements RankRequirementEvaluator {

        final Map<RankRequirementType, Boolean> checkedOnAsync = new EnumMap<>(RankRequirementType.class);
        private final ManualScheduler scheduler;
        boolean pass = true;
        boolean storedPass = true;
        boolean storedDies;

        Requirements(ManualScheduler scheduler) {
            this.scheduler = scheduler;
        }

        @Override
        public boolean passes(PlayerRef who, RankRequirement requirement) {
            checkedOnAsync.put(requirement.type(), scheduler.isRunningAsync());
            if (storedDies && readsStorage(requirement)) {
                throw new AssertionError("evaluator thread died");
            }
            return readsStorage(requirement) ? storedPass : pass;
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
        online(ALEX);
        rows.failSaves(true);

        CompletableFuture<RankupResult> result = rankup.rankUp(ALEX);
        scheduler.runAsync();

        assertThat(result.join().status()).isEqualTo(Status.FAILED);
        assertThat(owed.queued).containsExactly("40:default");
    }

    @Test
    void aHopTheSchedulerRefusesStillAnswersAndFreesThePlayer() {
        purse.balance = 200;
        online(ALEX);
        scheduler.shutDown();

        CompletableFuture<RankupResult> result = rankup.rankUp(ALEX);
        assertThatThrownBy(scheduler::runAsync).isInstanceOf(IllegalStateException.class);

        assertThat(result).isDone();
        assertThat(result.join().status()).isEqualTo(Status.RANKED_UP);
        CompletableFuture<RankupResult> next = rankup.rankUp(ALEX);
        assertThat(next.isDone() && next.join().status() == Status.BUSY).isFalse();
    }

    @Test
    void anErrorOnTheAsyncLaneStillAnswersAndFreesThePlayer() {
        purse.balance = 200;
        online(ALEX);
        requirements.storedDies = true;

        CompletableFuture<RankupResult> result = rankup.rankUp(ALEX);
        assertThatThrownBy(scheduler::runAsync).isInstanceOf(AssertionError.class);

        assertThat(result.join().status()).isEqualTo(Status.FAILED);
        assertThat(purse.balance).isEqualTo(200);
        requirements.storedDies = false;
        CompletableFuture<RankupResult> next = rankup.rankUp(ALEX);
        scheduler.runAsync();
        assertThat(next.join().status()).isEqualTo(Status.RANKED_UP);
    }

    @Test
    void aSaveThatDiesWithAnErrorRefundsTheChargeAndFreesThePlayer() {
        purse.balance = 100;
        online(ALEX);
        rows.dieOnSaves(true);

        CompletableFuture<RankupResult> result = rankup.rankUp(ALEX);
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

    /** One player's balance in whole units, with the total ever withdrawn so a refund is visible. */
    private static final class Purse implements RankEconomy {

        long balance;
        long withdrawn;
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
            withdrawn += take;
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
            return true;
        }
    }
}
